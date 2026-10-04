# Backend Auth MVP

## Configuration et verification

PostgreSQL et Redis locaux actifs ; charger `.env` avec `scripts/load-local-env.ps1`. JWT_SECRET obligatoire : 32 octets aleatoires minimum encodes en Base64. JWT_EXPIRATION_SECONDS vaut 900 par defaut. Ne pas reutiliser la cle publique de `.env.example` en production. Ne jamais versionner `.env`.

Depuis backend (PowerShell) :

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

## Endpoints

### POST /api/v1/auth/register — public

```json
{"name":"Demo User","email":"demo@example.test","password":"StrongPassword123!"}
```

201 : `{id,name,email,role,createdAt}`. Role USER obligatoire par defaut, aucun role fourni par le client. Nom nettoye, email strip/lowercase avant validation. Mot de passe de 12 caracteres minimum, 72 octets UTF-8 maximum, sans modification ni troncature. 400 : validation/JSON ; 409 : email existant. Register ne connecte pas automatiquement : appeler Login.

### POST /api/v1/auth/login — public

```json
{"email":"demo@example.test","password":"StrongPassword123!"}
```

200 : `{accessToken,tokenType:"Bearer",expiresIn:900,user:{id,name,email,role,createdAt}}`. 401 : meme erreur generique pour email inconnu et mauvais password. Mot de passe trop long ou requete invalide : 400.

### GET /api/v1/auth/me — protege

Header : `Authorization: Bearer <accessToken>`.

200 : `{id,name,email,role,createdAt}`. 401 : token absent, invalide, expire ou utilisateur supprime. Les autres routes dashboard /api/** exigent egalement un JWT ; POST /api/v1/evaluate utilise exclusivement une Environment API key (voir evaluation.md). Seuls POST Register/Login et GET /actuator/health sont publics. Un header Authorization fourni mais invalide est refuse, meme sur une route publique dashboard.

## Erreurs

Format commun : `{timestamp,status,code,message,path,fieldErrors}`. Les erreurs de validation donnent un message par champ sans valeur rejetee. Codes principaux : VALIDATION_ERROR (400), INVALID_REQUEST (400), INVALID_CREDENTIALS (401), UNAUTHORIZED (401), DUPLICATE_EMAIL (409). Aucune stacktrace ni valeur de mot de passe retournee.

## Securite

BCrypt cost 12, JWT HS256 avec id/issuer/expiration controles par Nimbus. Aucun cookie ou session HTTP ; utiliser HTTPS en production. Aucun refresh token, OAuth2 ou role avance. CORS dashboard limite a une origine exacte FRONTEND_ORIGIN, sans wildcard ni cookies (voir frontend.md). Les routes ne disposent pas encore de limite d'abus ; configurer une protection avant exposition publique. Un token reste valide jusqu'a expiration (ou suppression du User/rotation du secret).

V1 reste intacte. V2 normalise les emails existants et echoue en cas de doublons apres normalisation : resoudre explicitement les comptes concernes avant application.

## Tests

AuthIntegrationTest utilise PostgreSQL reel et la chaine Spring Security via MockMvc ; chaque test annule ses donnees. JwtServiceTest teste les claims, expiration, signature, issuer et configuration. Les tests Foundation/User restent presents. Validation locale finale de l'utilisateur : Maven Wrapper verify retourne BUILD SUCCESS, les 12 tests passent, Register/Login et les controles d'acces JWT fonctionnent ; Flyway V1 et V2 sont appliquees. PHASE 1 AUTH backend terminee et validee. Voir CURRENT_STATE.md.
