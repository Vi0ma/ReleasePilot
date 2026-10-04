# Revue de securite ciblee

Cette revue porte sur les choix et controles du MVP ; elle n'est pas une certification ou un audit externe.

| Sujet | Implementation / verification |
|---|---|
| Passwords | BCrypt cost 12, limite 72 octets UTF-8, pas de passwordHash dans UserResponse. Tests Register/Login et inspection des DTOs. |
| JWT | HS256 ; secret Base64 >=32 octets decodes, issuer/iat/exp/sub valides. User/role relus en PostgreSQL. Secret distinct par environnement ; tests signature/expiration/issuer/configuration. |
| API keys | SecureRandom, publicId indexe 128 bits, secret 256 bits ; uniquement SHA-256 du secret stocke, comparaison MessageDigest.isEqual. Secret affiche uniquement au POST de creation, no-store. |
| Revocation | Relue en PostgreSQL a chaque evaluation, sans cache. Tests de revocation et separation JWT/key. Une requete deja authentifiee peut terminer. |
| Ownership | Principal seul comme identite ; owner/parent verifie dans les services, 404 uniforme pour les ressources etrangeres. Tests isolation User/Project/Environment. |
| Demo Store | Cle uniquement BFF, projection explicite du JSON, redirects upstream refuses, pas de logs de credentials. Tests, smoke cle invalide et inspection des bundles/DOM/requetes navigateur. |
| CORS | Une origine dashboard exacte ; sans wildcard/cookies. Tests de preflight et refus des origines etrangeres. Evaluation non ouverte au navigateur. |
| Erreurs / audit | DTO d'erreur sans stacktrace/valeur rejetee ; logs d'erreur generiques, audit sans credentials. |
| Git / build | Aucun .env/.local tracke ; .env.example factices conserves. Contextes Docker excluent secrets et artefacts. `node scripts/verify-repository.mjs` compare les secrets locaux connus sans les imprimer. |
| Containers | Backend, BFF et Nginx non-root ; ports applicatifs exposes uniquement sur loopback, BFF Docker non publie. |

Le JWT reste dans sessionStorage, accessible au JavaScript de l'origine. Ce MVP ne rend pas de HTML utilisateur brut. Un logout supprime le token navigateur ; il n'y a pas de revocation individuelle ni refresh token. Rotation globale du secret ou suppression du User invalident l'acces correspondant.

HTTPS, protection brute-force/rate limiting, gestion centralisee des secrets et controles d'exploitation sont a preparer avant une exposition publique. Les variables runtime Docker peuvent etre lues par l'administrateur Docker : ne pas publier `docker compose config` ou un `docker inspect` contenant des credentials ; utiliser `config --quiet` pour valider. Aucun secret de production dans la CI : mot de passe et cle JWT publiques reserves aux services de test ephemeres.

`scripts/init-local-env.ps1` genere des valeurs locales aleatoires uniquement a la premiere configuration. Il ne remplace pas un `.env` existant ni le mot de passe d'un volume PostgreSQL existant. Pour changer ce mot de passe, changer egalement le credential PostgreSQL explicitement ; aucun reset automatique de volume.

Le scan couvre les fichiers versionnes et les nouveaux fichiers non ignores du workspace, pas une garantie de detection universelle ou de nettoyage de tout l'historique Git. Les templates et fixtures de test publics ne sont pas des secrets reels.
