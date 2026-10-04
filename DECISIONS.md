# DECISIONS.md — Architecture Decision Log

## ADR-001 — Modular Monolith
**Status:** Accepted

Première version en modular monolith pour livrer vite et éviter la complexité opérationnelle des microservices.

## ADR-002 — Java 21 + Spring Boot
**Status:** Accepted

Backend en Java 21 + Spring Boot pour REST, Security, JPA, Validation, Testing et Actuator.

## ADR-003 — PostgreSQL source of truth
**Status:** Accepted

Toutes les données métier persistantes vivent dans PostgreSQL.

## ADR-004 — Redis comme cache
**Status:** Accepted

Pattern Cache Aside. Redis accélère les évaluations mais n'est jamais la source de vérité.

## ADR-005 — JWT pour les humains
**Status:** Accepted

Le dashboard utilise email/password puis JWT.

## ADR-006 — API Key pour les applications
**Status:** Accepted

Les applications clientes et SDK utilisent une API key liée à un environnement.

## ADR-007 — Rollout déterministe
**Status:** Accepted

Stable hash de `userKey + ":" + flagKey`, bucket `0..99`. `Math.random()` est interdit.

## ADR-008 — REST first
**Status:** Accepted

REST pour le MVP. WebSocket/SSE seulement plus tard si besoin.

## ADR-009 — DTOs pour l'API
**Status:** Accepted

Les entités JPA ne sont jamais exposées directement.

## ADR-010 — MVP avant extras
**Status:** Accepted

Auth, projects, environments, flags, evaluation, rollout, Redis, audit, demo, tests, Docker, CI avant toute fonctionnalité avancée.

## ADR-011 — Foundation locale et migrations
**Status:** Accepted

Compose gere uniquement PostgreSQL 17 et Redis 7.4 en Foundation ; Java et Vite tournent sur l'hote. Les ports des services sont exposes sur loopback. PostgreSQL dispose d'un volume, Redis reste ephemere. Variables de processus pour Spring, `.env` ignore pour Compose et chargement explicite PowerShell. Aucun secret reel versionne.

Flyway est actif conformement au master guide, sans migration metier. Hibernate utilise `validate`. Le test Foundation utilise les vrais services locaux pour verifier les connexions et Actuator ; aucun substitut H2. Maven Wrapper Apache 3.3.4 fixe Maven 3.9.12, sans JAR versionne. Spring Boot 3.5.16 fixe la version initiale ; sa resolution et sa compilation restent a verifier. Aucun changement d'architecture.

## ADR-012 — Persistance User (Phase 1A)
**Status:** Accepted

La table `users` est creee par Flyway V1. Identifiant `BIGINT` identity PostgreSQL, mappe en `Long` avec `GenerationType.IDENTITY`. Le seul role initial est `USER`, stocke en texte avec `EnumType.STRING` et une contrainte SQL ; les permissions de projet seront traitees ulterieurement.

`Instant` est mappe sur `TIMESTAMP WITH TIME ZONE`. Les callbacks JPA initialisent les deux timestamps a la creation et actualisent `updated_at` lors des modifications JPA ; `created_at` est non modifiable par JPA. Aucun trigger ni valeur par defaut SQL : toute future ecriture SQL directe devra fournir les timestamps.

Email unique au niveau PostgreSQL, comparaison exacte pour cette etape. La normalisation des emails sera decidee avec Register. `existsByEmail` prepare le controle d'inscription ; la contrainte UNIQUE reste la garantie contre les insertions concurrentes. `password_hash` contient une valeur deja hashee fournie par la future couche authentication ; aucun calcul de hash en Phase 1A. Longueurs : name 100, email 254, password_hash 255, role 20. Aucun changement d'architecture ni nouvelle dependance.

## ADR-013 — Backend Auth MVP
**Status:** Accepted

BCrypt cost 12 avec sel genere par Spring Security. Register exige au moins 12 caracteres et au plus 72 octets UTF-8, limite technique BCrypt ; Login applique aussi la limite en octets. Aucun mot de passe tronque ou normalise. Les emails sont strip/lowercase avec Locale.ROOT avant validation. Flyway V2 normalise les emails existants et impose leur forme canonique en SQL ; une collision preexistante bloque la migration plutot que fusionner des comptes. UNIQUE reste la garantie concurrente, mappee vers HTTP 409.

JWT HS256 avec sub = id User, iss = https://releasepilot.local (identifiant uniquement, aucun appel reseau), iat et exp. JWT_SECRET obligatoire, Base64 avec au moins 32 octets decodes ; generer une cle aleatoire propre a chaque environnement. Expiration par defaut 900 secondes, configurable entre 60 et 86400 secondes. Validation de signature, algorithme, issuer, dates et presence des claims. Pas de refresh token ni de session serveur. Spring Security JOSE/Nimbus fournit les codecs JWT ; aucun flux OAuth2, provider ou Resource Server n'est configure.

SecurityFilterChain stateless, cache de requetes desactive, login formulaire/HTTP Basic/logout desactives. Filtre OncePerRequestFilter instancie uniquement dans la chaine Security pour eviter une double execution servlet. PUBLIC : POST register/login et GET health. Tout /api/** restant exige un Bearer valide ; autres routes refusees. Principal minimal (id), utilisateur/role relus en PostgreSQL a chaque requete : un utilisateur supprime perd l'acces. CSRF desactive car aucun cookie d'authentification ; CORS cross-origin non ouvert tant que le frontend auth n'est pas implemente. Utiliser HTTPS en production.

Register retourne UserResponse (201), Login retourne AuthResponse (200) et GET /api/v1/auth/me retourne UserResponse. Aucun hash ni password dans les DTOs de sortie. Erreurs JSON communes pour MVC et Security ; valeurs rejetees et stacktraces non exposees. Login utilise un hash factice si l'email est inconnu et retourne la meme erreur generique pour email inconnu et mauvais password.

Deconnexion MVP cote client par suppression du token ; un token emis reste valide jusqu'a exp sauf suppression du User ou rotation globale du secret. La protection contre abus/brute force devra etre configuree avant exposition publique (par exemple au reverse proxy). Aucun mecanisme de blocage de comptes ni rate limiter ajoute a cette etape.

## ADR-014 — Core Domain et authorization par proprietaire
**Status:** Accepted

Project appartient a un seul User ; aucun transfert ni RBAC supplementaire. Keys explicites, stables et immuables, ASCII `[a-z0-9_-]+`, uniques par proprietaire pour Project, par Project pour Environment/FeatureFlag. Noms et descriptions restent modifiables. Ressource inconnue, etrangere ou sous un mauvais parent : meme HTTP 404, afin de ne pas divulguer son existence. Principal JWT comme seule source d'identite.

Creation Project + trois Environments (`development`, `staging`, `production`) + audit dans une transaction. Creation Flag + une configuration par Environment + audit egalement atomique. Configuration initiale OFF/0 ; enabled et rolloutPercentage 0..100 sont independants. PATCH exige les deux champs. Aucun moteur d'evaluation dans cette phase.

FK SQL et contraintes UNIQUE assurent l'integrite. FlagConfiguration conserve project_id en plus des relations flag/environment : deux FK composites garantissent qu'ils appartiennent au meme Project, meme en SQL direct. IDs BIGINT identity, relations JPA ManyToOne LAZY sans cascade REMOVE, timestamps Instant/TIMESTAMP WITH TIME ZONE via callbacks JPA. Flyway V3 uniquement ; V1 et V2 inchangees, Hibernate validate.

Les mutations d'un Project existant et de ses enfants prennent un verrou pessimiste sur le Project, toujours avant modification, pour serialiser les ecritures et les suppressions. Les suppressions sont physiques et explicites : configurations -> flags -> environments -> project. Aucun ON DELETE CASCADE. DELETE Project emet PROJECT_DELETED avec les nombres d'enfants supprimes ; pas d'evenement FLAG_DELETED pour chaque enfant supprime implicitement.

Audit dans la meme transaction que la mutation, metadata JSONB avec snapshots raisonnables avant/apres sans credentials. projectId/entityId historiques sans FK vers les ressources supprimables ; actorUserId et ownerUserId gardent une FK users. ownerUserId permet au proprietaire historique de consulter l'audit apres suppression du Project. Aucun endpoint de modification d'audit. Consultation paginee, size 1..100, ordre createdAt/id decroissant. Les listes courantes restent simples pour le MVP.

Documentation API statique OpenAPI dans docs/openapi-core.yaml, selon la convention Auth existante. Aucune dependance supplementaire, aucun cache Redis metier, frontend ou changement d'architecture.

## ADR-015 — Environment API keys et separation Security (Phase 3)
**Status:** Accepted

Format `rp_<publicId>.<secret>` : publicId de 128 bits aleatoires en hexadecimal (32 caracteres), secret de 256 bits aleatoires en Base64 URL sans padding (43 caracteres), generes avec SecureRandom. PostgreSQL conserve publicId UNIQUE indexe et SHA-256 du secret encode UTF-8 (hexadecimal 64 caracteres), jamais la cle complete. Lookup direct par publicId, comparaison MessageDigest.isEqual. SHA-256 convient a ce secret aleatoire de forte entropie ; BCrypt reste reserve aux mots de passe humains.

Plusieurs cles par Environment, nom descriptif, createdAt/revokedAt ; hash exclu des DTOs et protege par JsonIgnore sur l'entity. Secret disponible uniquement au POST de creation (Cache-Control: no-store). Revocation idempotente, verifiee en PostgreSQL a chaque authentification, sans cache. Rotation par creation puis revocation. Management sous JWT et controle proprietaire/parent avec 404, mutations et audit atomiques. API_KEY_CREATED/API_KEY_REVOKED n'enregistrent ni secret ni hash. Suppression Project explicite : configurations -> flags -> API keys -> environments -> Project ; audit historique conserve.

Deux SecurityFilterChain stateless ordonnees : la premiere traite exclusivement /api/v1/evaluate via X-API-Key ; la seconde conserve les regles JWT dashboard. Pas d'acceptation de JWT pour evaluation ni de cle pour dashboard. Cle absente/invalide/revoquee ou header multiple : 401. PostgreSQL indisponible pour authentifier la cle : 503. Scope Environment/Project uniquement depuis le principal, champs JSON supplementaires refuses. Pas de nouvelle dependance, session, event bus ou role.

## ADR-016 — Rollout SHA-256 et cache versionne (Phase 3)
**Status:** Accepted

SHA-256 sur UTF-8(userKey + ":" + flagKey), entier positif big-endian forme par les 32 octets complets, modulo 100. Bucket 0..99 ; userKey conserve exactement, flagKey ASCII immuable. Vecteur de reference : user-123:new_checkout -> 60. OFF domine ; ON/0 refuse, ON/100 accepte, sinon bucket < percentage. Moteur pur sans JPA, Redis ou HTTP. Flag absent : 200 false/FLAG_NOT_FOUND, pas de cache negatif.

Redis cache-aside via un port ConfigurationCache : cle `flag-config:v1:{environmentId}:{projectEvaluationRevision}:{flagKey}`, valeur uniquement enabled/rolloutPercentage, TTL configurable EVALUATION_CACHE_TTL_SECONDS (60 par defaut, 1..3600). Un hit evite la lecture PostgreSQL de configuration ; l'authentification continue a lire PostgreSQL. Erreurs Redis et valeurs malformees : fallback PostgreSQL avec avertissement generique, jamais de secret journalise. Redis ne decide jamais du scope ni de la revocation. Le health global peut rester DOWN lorsque Redis est indisponible sans empecher l'evaluation.

Une simple eviction ne protege pas contre une lecture ancienne qui recharge Redis apres mutation, ni contre le retour d'un cache obsolete apres une panne. Flyway V4 ajoute projects.evaluation_revision ; creation/suppression flag et modification config incrementent cette revision sous le verrou Project existant, dans la transaction PostgreSQL. La prochaine authentification lit la revision commitee et ignore les generations precedentes. TransactionSynchronization evince egalement les anciennes entrees concernees apres commit ; rollback n'invalide rien. Les generations restantes expirent au TTL. Compromis simple : toute mutation de configuration invalide logiquement le cache du Project entier, sans verrou Redis ni bus d'evenements. Les requetes deja authentifiees peuvent terminer sur la generation lue avant le commit.

V4 cree aussi environment_api_keys et etend la contrainte des actions d'audit. V1/V2/V3 conservent leurs checksums, Hibernate reste validate, aucune cascade de suppression. Contrat et limites documentes dans docs/evaluation.md et docs/openapi-evaluation.yaml.

## ADR-017 — Product Frontend et integration dashboard (Phase 4)
**Status:** Accepted

React/TypeScript strict/Vite et dependances existantes conserves. Client fetch central, DTOs correspondant aux records Java et appels regroupes par domaine. Browser History pour les routes finies du MVP, useSyncExternalStore/popstate pour back/forward, vrais liens sans interception des clics modifies. Pas de bibliotheque additionnelle de routing, state, UI ou tests. Serveur de production SPA a configurer pour index.html sur les routes frontend.

JWT dans sessionStorage uniquement ; Login est suivi de /auth/me, token present au demarrage impose restauration /me avant dashboard. 401 dashboard et expiration nettoient la session ; 401 Login public reste une erreur du formulaire. Echec reseau de restauration permet retry, sans annoncer une session invalide. Aucun cookie, localStorage, secret ou mot de passe persiste. sessionStorage reste accessible au JavaScript de l'origine ; le logout MVP ne revoque pas un JWT deja emis.

Preflight reel Vite -> backend initialement refuse 401 ; ajout CORS minimal dans la chaine JWT dashboard uniquement. FRONTEND_ORIGIN est une origine exacte configurable (http://127.0.0.1:5173 par defaut), sans wildcard ni credentials, headers Authorization/Content-Type/Accept. Chaine Evaluation/API key conservee, aucune nouvelle fonctionnalite backend ni migration. Vite fixe host/port et strictPort pour eviter un changement silencieux d'origine. VITE_API_BASE_URL centralisee ; variables VITE_ publiques.

Toggles non optimistes : ON/OFF change uniquement apres reponse serveur. Slider avec draft local, PATCH au commit/clavier/blur, verrou contre appels concurrents et restauration en cas d'echec. API key complete dans l'etat ephemere de la modal seulement, efface a fermeture/changement de contexte, jamais persiste ; metadata seules dans les listes. Confirmations natives dialog pour les operations destructrices. Audit affiche seulement les champs connus, pas de JSON metadata brut.

Dashboard sans metriques inventees : un appel Projects et recents tries ; total de flags seulement sur Overview Project. Compte courant en lecture seule/logout puisque aucun endpoint profil modifiable n'existe. Tests utiles avec le runner Node 22, sans nouvelle dependance ; trois tests CORS backend preservent les regles d'authentification. Details dans docs/frontend.md.

## ADR-018 — Demo Store independante et secret serveur (Phase 5)
**Status:** Accepted

`demo-store/` est une application cliente externe React/TypeScript/Vite (5174), distincte du dashboard. Un petit BFF Node 22 HTTP/fetch natif (3001, loopback) garde RELEASEPILOT_API_KEY cote serveur et appelle le contrat existant POST /api/v1/evaluate. Ce choix garde la demonstration simple sans modifier le backend Java 21/Spring Boot, ses migrations ou son modular monolith. Aucun SDK ou nouveau domaine e-commerce dans ReleasePilot.

Vite proxy /api en dev/preview ; aucun CORS supplementaire. DTO public explicite flagKey/enabled/reason, no-store, validation userKey/corps, timeout et refus des redirects upstream. Aucun secret navigateur, log sensible ou cache Demo Store. Erreur technique : enabled=false, reason Demo Store RELEASEPILOT_UNAVAILABLE, fallback Checkout V1 utilisable ; le frontend applique aussi V1 si le BFF est inaccessible.

Visitor crypto.randomUUID conserve dans localStorage, regenerable uniquement par l'outil de demo ; decision de rollout uniquement chez ReleasePilot. Panier fictif local, formulaires non persistes, aucun paiement ou commande stockee. Fixture locale optionnelle creee via API publiques dans un compte dedie, credentials/.env ignores ; smoke restaure la configuration et ne touche pas aux projets existants. Pas de Phase 6.

## ADR-019 — Livraison locale reproductible et CI (Phase 6 finale)
**Status:** Accepted

Compose conserve PostgreSQL/Redis par defaut pour le dev et ajoute le profil full avec backend, dashboard, Demo Store BFF et storefront. Meme volume PostgreSQL, aucun reset. Ports full 18080/15173/15174 distincts du dev pour permettre une validation sans interrompre les applications hote. Backend JDK/JRE 21 multi-stage, web Node 22.23.3 build puis Nginx non-root, BFF Node natif sans dependances runtime ; quatre applications non-root et healthchecks.

Le dashboard Docker utilise une base URL publique vide et Nginx /api vers backend ; la boutique proxie /api vers BFF prive. DNS Docker re-resolu pour supporter recreation des services. Contextes excluent .env/.local/node_modules/dist/target. Secrets fournis au runtime, jamais en build args/frontend. demo-store/.env facultatif au demarrage pour fallback V1 sans cle ; ajout/rotation de cle requiert recreation du BFF, jamais rebuild de la boutique. Le host BFF configurable conserve loopback par defaut en dev.

CI GitHub Actions sur push/pull_request, permissions contents:read : Java 21 + PostgreSQL/Redis de test + Maven verify ; matrice dashboard/Demo Store Node 22.23.3 avec npm ci/test/build. Credentials de test publics, aucun secret prive, aucun CD automatique ou deploiement cloud. Image backend package sans tests durant le build car les vrais services sont verifies separement par verify/CI. Tests Linux sont egalement executes localement pour verifier la compatibilite du workflow ; un run GitHub heberge reste a constater apres publication.

Initialisation PowerShell cree les secrets locaux aleatoires uniquement si .env absent et ne change aucun credential existant. Documentation OpenAPI statique conservee, 23 operations validees contre les controllers et enums d'audit ; pas de nouvelle dependance backend pour Swagger UI. Aucun changement de logique metier, migration ou nouvelle phase.
