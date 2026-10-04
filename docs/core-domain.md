# Core Domain — Phase 2

Backend uniquement, architecture controller -> service -> repository. PostgreSQL et Flyway controlent les donnees ; Hibernate reste en `ddl-auto=validate`. V3 definit le Core Domain ; [Phase 3](evaluation.md) ajoute API keys, evaluation et cache Redis via V4.

## Contrat HTTP

Toutes les routes exigent `Authorization: Bearer <JWT>` obtenu via Login. [Contrat OpenAPI](openapi-core.yaml) importable dans Swagger Editor ; [Auth](auth.md) conserve son propre contrat. Pas de Swagger UI serveur ajoute dans cette phase.

| Methode | Route | Resultat |
| --- | --- | --- |
| POST | /api/v1/projects | 201, Project et trois Environments crees |
| GET | /api/v1/projects | 200, liste des Projects du proprietaire |
| GET / PUT / DELETE | /api/v1/projects/{projectId} | 200 / 200 / 204 |
| GET | /api/v1/projects/{projectId}/environments | 200, liste des Environments |
| GET | /api/v1/projects/{projectId}/environments/{environmentId} | 200, Environment |
| POST / GET | /api/v1/projects/{projectId}/flags | 201 / 200 |
| GET / PUT / DELETE | /api/v1/projects/{projectId}/flags/{flagId} | 200 / 200 / 204 |
| GET | /api/v1/projects/{projectId}/flags/{flagId}/configurations | 200, configurations du flag |
| GET / PATCH | /api/v1/flags/{flagId}/environments/{environmentId} | 200, configuration |
| GET | /api/v1/projects/{projectId}/audit?page=0&size=20 | 200, audit pagine |

Create Project/Flag : `{"name":"Demo Store","key":"demo-store","description":"Optionnelle"}`. Keys explicites et immuables, 1..100 caracteres ASCII minuscules/chiffres/tiret/underscore. Name obligatoire, 100 caracteres maximum ; description optionnelle, 2000 maximum. Les espaces autour des champs sont retires. Project key unique pour un proprietaire ; flag key unique dans son Project. Doublon : 409. PUT Project/Flag exige name ; description absente ou null efface la description ; key et owner ne sont pas modifiables.

Creation Project transactionnelle avec `development`, `staging`, `production` et PROJECT_CREATED. Les Environments ne disposent pas encore de CRUD de creation/modification. Creation Flag transactionnelle avec une configuration par Environment, `enabled=false`, `rolloutPercentage=0`, et FLAG_CREATED.

PATCH configuration exige les deux champs : `{"enabled":true,"rolloutPercentage":25}`. Enabled et pourcentage sont independants : OFF peut conserver un pourcentage pour une activation future. Bornes inclusives 0..100, champ manquant ou hors plage : 400. La mutation persiste le pourcentage et invalide le cache apres commit ; l'evaluation utilisateur se fait dans l'API dediee de Phase 3.

## Authorization et suppression

Principal JWT uniquement : aucun owner fourni par le client. Les Services verifient le proprietaire et l'appartenance au parent de la route. Ressource inconnue, d'un autre User ou sous un mauvais Project/Environment : meme 404 NOT_FOUND. Token absent/invalide/expire : 401. Cette strategie limite la divulgation des identifiants existants.

Les ecritures sur un Project existant prennent un verrou PostgreSQL sur ce Project jusqu'a la fin de transaction, pour serialiser creation/modification/suppression de ses enfants. Contraintes UNIQUE SQL comme garantie finale. Aucun transfert de proprietaire dans le MVP.

DELETE Flag supprime explicitement ses configurations puis le Flag. DELETE Project supprime explicitement configurations, Flags, API keys, Environments puis Project, dans cet ordre. Pas de cascade JPA REMOVE ni SQL ON DELETE CASCADE. Les audits sont conserves. PROJECT_DELETED inclut les nombres d'enfants supprimes ; les suppressions implicites de Flags par DELETE Project ne generent pas de FLAG_DELETED individuel. Les caches concernes sont invalides apres commit (voir Phase 3).

## Audit

Sept actions Core : PROJECT_CREATED, PROJECT_UPDATED, PROJECT_DELETED, FLAG_CREATED, FLAG_UPDATED, FLAG_DELETED, FLAG_CONFIGURATION_UPDATED. Phase 3 ajoute API_KEY_CREATED et API_KEY_REVOKED. Chaque evenement est ecrit dans la transaction de la mutation ; un echec d'audit annule la mutation. Metadata JSONB contient uniquement des snapshots de ressources et des changements avant/apres, jamais de credentials.

AuditEvent conserve actorUserId et ownerUserId avec FK users. ProjectId et entityId sont des identifiants historiques sans FK vers des ressources supprimables. Le proprietaire historique peut encore consulter l'audit apres suppression du Project, grace a ownerUserId ; un autre User recoit 404. Les IDs de Project ne sont jamais reutilises par l'application.

Reponse audit : `content`, `page`, `size`, `totalElements`. Ordre `createdAt DESC, id DESC`, page >= 0, size 1..100 (20 par defaut). Les listes Project/Flag/Environment restent simples, triees par id.

## Verification locale

Depuis la racine PowerShell, services reels actifs :

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
. ./scripts/load-local-env.ps1
docker compose up -d --wait --wait-timeout 60
cd backend
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

Dans un autre terminal : `Invoke-RestMethod http://localhost:8080/actuator/health` doit retourner UP. Les tests CoreDomainIntegrationTest utilisent PostgreSQL reel, MockMvc et JWT. Transactions de test annulees, sans H2 ni ddl-auto=create/update. Les sequences peuvent avancer meme apres rollback.

Les tests couvrent CRUD, defaults, contraintes SQL, isolation User/Project, JWT obligatoire, configuration 0..100, audit et conservation apres suppression. Le resultat reel de la session est consigne dans CURRENT_STATE.md.
