# Evaluation Engine — Phase 3

Le dashboard utilise le JWT Bearer existant. `POST /api/v1/evaluate` utilise exclusivement le header `X-API-Key` : un JWT seul ne suffit pas, et une API key ne donne aucun acces au dashboard. Toutes les chaines Security sont stateless.

## Management des cles (JWT)

Base : `/api/v1/projects/{projectId}/environments/{environmentId}/api-keys`.

| Methode | Route | Resultat |
|---|---|---|
| POST | base | 201, `{ "key": { ...metadata }, "apiKey": "rp_..." }` |
| GET | base | 200, liste des metadata, y compris les cles revoquees |
| DELETE | base + `/{keyId}` | 204, revocation idempotente |

POST accepte `{ "name": "checkout-service" }`, nom non vide de 100 caracteres maximum. Metadata : id, environmentId, name, prefix, createdAt, revokedAt. Le secret et son hash ne sont jamais presents dans les listes. Ressource absente, autre proprietaire ou mauvais parent : 404. Les mutations prennent le verrou Project existant et enregistrent API_KEY_CREATED/API_KEY_REVOKED dans leur transaction, sans secret ni hash dans l'audit.

Format : `rp_<publicId>.<secret>`. publicId contient 16 octets SecureRandom encodes en 32 caracteres hexadecimaux minuscules ; secret contient 32 octets SecureRandom encodes en Base64 URL sans padding (43 caracteres). La cle complete n'est retournee qu'a la creation, avec Cache-Control: no-store. Le client doit la conserver dans son gestionnaire de secrets.

PostgreSQL conserve le publicId UNIQUE indexe et SHA-256 des octets UTF-8 du secret encode, sous forme de 64 caracteres hexadecimaux. Le lookup utilise publicId, puis MessageDigest.isEqual compare les hashes. Une fonction lente de password hashing n'est pas necessaire pour ce secret aleatoire de 256 bits. Pas de cle complete en base ni dans Redis. La revocation est relue en PostgreSQL a chaque evaluation ; pas de cache d'authentification. Une requete deja authentifiee peut terminer pendant une revocation. Rotation MVP : creer une nouvelle cle, mettre a jour le client, revoquer l'ancienne ; aucun endpoint supplementaire.

## Evaluation

```http
POST /api/v1/evaluate
X-API-Key: <cle conservee par le client>
Content-Type: application/json

{"flagKey":"new_checkout","userKey":"user-123"}
```

```json
{"flagKey":"new_checkout","enabled":true,"reason":"ROLLOUT_MATCH"}
```

flagKey : ASCII `[a-z0-9_-]+`, maximum 100 caracteres. userKey : non vide, maximum 256 caracteres, conserve exactement, sans normalisation. Les champs supplementaires, notamment environmentId, sont refuses (400). Environment et Project sont determines uniquement par la cle. Meme flagKey dans deux Projects/Environments : seule la configuration du scope authentifie est utilisee.

| Cas | enabled | reason |
|---|---|---|
| Flag absent du scope | false | FLAG_NOT_FOUND |
| Configuration OFF | false | FLAG_DISABLED |
| ON, rollout 100 | true | ROLLOUT_MATCH |
| ON, rollout 0 | false | ROLLOUT_MISS |
| ON, bucket < rolloutPercentage | true | ROLLOUT_MATCH |
| ON, bucket >= rolloutPercentage | false | ROLLOUT_MISS |

Decision valide, y compris flag absent : HTTP 200. Cle absente, invalide, revoquee ou headers X-API-Key multiples : 401. PostgreSQL indisponible lors de l'authentification : 503. Erreurs JSON communes sans stacktrace. Aucun identifiant interne, hash, userKey ni secret dans la reponse.

## Hashing deterministe

SHA-256 de UTF-8(`userKey + ":" + flagKey`). Interpretrer les **32 octets complets** comme un entier positif big-endian, puis modulo 100. Bucket entier 0..99, stable entre executions et redemarrages. Exemple reproductible : `user-123:new_checkout` donne 60 ; ON/60 est false, ON/61 est true. Le flagKey influence la distribution. Aucun tirage aleatoire ; SecureRandom sert uniquement a generer les API keys. Le separateur est sans ambiguite puisque flagKey interdit `:`.

## Redis cache-aside et coherence

Cle : `flag-config:v1:{environmentId}:{projectEvaluationRevision}:{flagKey}`. Valeur JSON minimale : enabled et rolloutPercentage. TTL 60 secondes par defaut, variable `EVALUATION_CACHE_TTL_SECONDS` (1..3600). Pas de cache des flags absents.

Apres authentification PostgreSQL, un hit Redis evite la requete de configuration. Un miss/une valeur malformee lit PostgreSQL, puis alimente Redis. Une panne Redis sur get/set/delete produit un avertissement generique et ne bloque pas l'evaluation ni les mutations PostgreSQL. Le timeout Redis existant borne chaque tentative ; une panne peut donc augmenter la latence. Le health Actuator global continue de signaler Redis DOWN, alors que l'Evaluation API utilise le fallback PostgreSQL.

Creation/suppression d'un flag et modification de configuration incrementent `projects.evaluation_revision` dans la meme transaction PostgreSQL, sous le verrou Project existant. Apres commit, les anciennes entrees concernees sont aussi supprimees physiquement via TransactionSynchronization. Pas d'invalidation en cas de rollback. Noms/descriptions ne changent pas la decision et les keys restent immuables.

La revision empeche qu'une ancienne lecture ecrive une valeur obsolete apres l'invalidation, ou qu'une panne Redis ressuscite une configuration perimee a sa reprise. L'authentification suivante relit la revision commitee et utilise une autre cle ; les anciennes generations expirent au TTL. Compromis MVP : une mutation invalide logiquement tout le cache du Project. Une evaluation deja authentifiee peut terminer sur la generation lue avant le commit.

Suppression Project : configurations -> flags -> API keys -> environments -> Project, sans cascade. Les cles supprimees ne peuvent plus s'authentifier ; les entrees courantes sont evincees apres commit, les anciennes expirent. L'audit historique survit ; PROJECT_DELETED inclut deletedApiKeys, sans evenement de revocation individuel pour chaque enfant supprime implicitement.

## Validation locale

Avec PostgreSQL et Redis Compose actifs, depuis la racine PowerShell :

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
. ./scripts/load-local-env.ps1
cd backend
.\mvnw.cmd test
.\mvnw.cmd verify
```

Les nouveaux tests d'integration utilisent les vrais services. Ils creent leurs propres fixtures et nettoient uniquement ces donnees/cles ; aucun reset de base ni FLUSHALL Redis. Le test de panne Redis utilise un autre contexte Spring avec une adresse Redis inaccessible et le meme PostgreSQL reel.

Contrat importable dans Swagger Editor : [openapi-evaluation.yaml](openapi-evaluation.yaml). Resultats reels de validation : [CURRENT_STATE.md](../CURRENT_STATE.md).
