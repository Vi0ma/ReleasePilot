# Commandes Windows PowerShell

Depuis la racine sauf indication contraire. Dev : Java 21/JAVA_HOME, Node 22.23.3+, npm, Docker Desktop Linux containers. Docker complet : Compose 2.24+ et PowerShell ; aucun Java/Node hote requis pour les containers. Les scripts setup/smoke facultatifs necessitent Node/npm.

## Premiere configuration

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
./scripts/init-local-env.ps1
. ./scripts/load-local-env.ps1
```

init cree `.env` avec secrets PostgreSQL/JWT aleatoires, jamais affiches, ou conserve le fichier existant. Ne pas changer seulement POSTGRES_PASSWORD pour un volume existant : ce script ne fait aucune rotation du credential PostgreSQL.

## Mode A — developpement

Terminal backend, racine :

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
. ./scripts/load-local-env.ps1
docker compose config --quiet
docker compose up -d --wait --wait-timeout 60
cd backend
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

Backend : http://localhost:8080. Terminal dashboard, racine :

```powershell
cd frontend
npm ci
npm test
npm run build
npm run dev
```

Dashboard : http://127.0.0.1:5173, origine CORS exacte. FRONTEND_ORIGIN dans `.env` racine ; VITE_API_BASE_URL public dans frontend/.env si necessaire. Le modele pointe vers localhost:8080.

Terminal Demo BFF, racine :

```powershell
cd demo-store
npm ci
if (-not (Test-Path -LiteralPath .env)) { Copy-Item .env.example .env }
# Editer .env : vraie cle Production cote serveur.
npm test
npm run build
npm run server
```

Quatrieme terminal, racine :

```powershell
cd demo-store
npm run dev
```

Boutique : http://127.0.0.1:5174 ; BFF : http://127.0.0.1:3001. API key dans demo-store/.env, jamais VITE_.

## Mode B — Docker complet

Racine, apres initialisation `.env` :

```powershell
docker compose --profile full config --quiet
docker compose --profile full up --build -d --wait --wait-timeout 180
docker compose --profile full ps
Invoke-RestMethod http://127.0.0.1:18080/actuator/health
```

Backend 18080, dashboard http://127.0.0.1:15173, boutique http://127.0.0.1:15174. BFF uniquement reseau Docker. Ports configurables : DOCKER_BACKEND_PORT / DOCKER_DASHBOARD_PORT / DOCKER_DEMO_PORT ; adapter les URL si changes. CORS Docker utilise automatiquement le port dashboard. PostgreSQL/Redis sont partages avec le dev, aucune reinitialisation.

Sans cle, boutique sur V1. Register/Login, creer Project Demo Store / flag new_checkout, generer une cle Production et creer demo-store/.env depuis son modele. Les deux BFF locaux peuvent utiliser la meme cle ; Docker remplace seulement la base URL par backend:8080. Apres ajout/rotation de cle (pas apres modification de flag) :

```powershell
docker compose --profile full up -d --no-deps --force-recreate demo-bff
```

Pas de rebuild frontend. Les proxies Nginx re-resolvent les noms Docker apres recreation ; leur cache DNS peut prendre jusqu'a 5 secondes pour se renouveler.

## Fixture dediee facultative

La demo manuelle ne necessite aucune fixture technique. Pour une fixture reutilisable par smoke, **sans demo-store/.env existant** :

```powershell
cd demo-store
# Docker ; omettre cette variable pour le backend dev localhost:8080.
$env:RELEASEPILOT_BASE_URL = 'http://127.0.0.1:18080'
npm run setup
Remove-Item Env:RELEASEPILOT_BASE_URL
```

setup refuse d'ecraser `.env`, cree un seul compte/projet et conserve les credentials dans demo-store/.local/fixture.json pour Login dans le dashboard. Aucun secret imprime. Reutiliser cette fixture, ne pas multiplier les projets. Appliquer la cle au BFF Docker avec la recreation ci-dessus. Pour un projet configure manuellement, utiliser la demo visuelle du README plutot que ce smoke.

## Tests et builds

Arreter Vite avec Ctrl+C **avant npm ci** sur Windows (binaires verrouilles). Garder PostgreSQL/Redis actifs. Racine :

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
. ./scripts/load-local-env.ps1
Push-Location backend
.\mvnw.cmd verify
Pop-Location

Push-Location frontend
npm ci
npm test
npm run build
Pop-Location

Push-Location demo-store
npm ci
npm test
npm run build
Pop-Location

node scripts/verify-repository.mjs
git diff --check
```

## Smoke de la fixture existante

Dev : backend, BFF et Vite actifs. Depuis demo-store : `npm run smoke`.

Docker, depuis la racine :

```powershell
Push-Location demo-store
$env:RELEASEPILOT_BASE_URL = 'http://127.0.0.1:18080'
$env:DEMO_STORE_ORIGIN = 'http://127.0.0.1:15174'
npm run smoke
Remove-Item Env:RELEASEPILOT_BASE_URL
Remove-Item Env:DEMO_STORE_ORIGIN
Pop-Location
```

Smoke verifie Login/Project, OFF, 100%, 30% avec repetition stable, OFF, Audit et cle invalide ; restaure la configuration dans finally. Ne pas lancer deux smokes ou modifier ce flag simultanement.

## Health, logs et arret

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health # dev
Invoke-RestMethod http://127.0.0.1:18080/actuator/health # Docker
docker compose --profile full ps
docker compose --profile full logs --tail 80 backend
# Applications Docker seulement, garder l'infrastructure dev :
docker compose --profile full stop dashboard demo-store demo-bff backend
# Tout arreter, conserver le volume :
docker compose --profile full down
```

Dev : Ctrl+C dans chaque terminal. `down -v` supprimerait les donnees, ne pas l'utiliser pour un arret normal. Ne pas partager une configuration interpolant des secrets.

## CI

`.github/workflows/ci.yml` : Java 21 + PostgreSQL/Redis de test + mvnw verify ; npm ci/test/build dans les deux apps web. Credentials de test publics, aucun deploiement automatique. Les validations locales ne constituent pas un run GitHub Actions heberge ; verifier celui-ci apres publication.
