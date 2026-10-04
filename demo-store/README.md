# Morrow — ReleasePilot Demo Store

Application externe independante : boutique fictive React + TypeScript + Vite, panier local et deux checkouts. Aucun paiement, compte client, commande persistante ou base e-commerce. Les illustrations sont en CSS, sans service externe.

## Architecture

```text
Browser (5174) -- POST /api/feature-evaluation {userKey} --> Vite proxy
  --> Demo Store BFF Node (3001)
  --> ReleasePilot (8080) POST /api/v1/evaluate {flagKey,userKey}
      X-API-Key: server secret
  <-- {flagKey,enabled,reason} <-- BFF explicit public DTO <-- browser
```

Node 22 fournit HTTP, fetch et timeouts sans framework additionnel. Le backend principal ReleasePilot reste Java 21/Spring Boot et n'est pas modifie. Le BFF n'est ni un service du modular monolith ni un SDK.

Le client `server/releasePilotClient.mjs` est le seul endroit appelant l'Evaluation API. Timeout de 2.5 secondes, verification du JSON, du flag, du boolean et des reason codes. Les redirects sont refuses pour proteger le header secret. Aucune reponse upstream brute ni exception sensible n'est exposee ou journalisee. Aucun cache Demo Store.

## Configuration et lancement

Prerequis : Node 22.23.3+, npm ; ReleasePilot backend (8080), PostgreSQL et Redis actifs. Dashboard distinct : http://127.0.0.1:5173.

Depuis la racine :

```powershell
cd demo-store
npm install
Copy-Item .env.example .env # uniquement si .env n'existe pas
```

Configurer `.env` :

```dotenv
RELEASEPILOT_BASE_URL=http://localhost:8080
RELEASEPILOT_API_KEY=replace-me
RELEASEPILOT_FLAG_KEY=new_checkout
```

Remplacer `replace-me` par une API key Production. Ces variables sont lues uniquement par Node (`--env-file-if-exists`), jamais par React. Ne jamais prefixer un secret par `VITE_`. `.env`, `.local`, `node_modules` et `dist` sont ignores. `.env.example` ne contient que des valeurs factices. La cle n'entre pas dans le bundle, le stockage navigateur, les requetes navigateur ni le panneau de demo. Seul le BFF envoie `X-API-Key`.

Deux terminaux dans `demo-store/` :

```powershell
npm run server # BFF http://127.0.0.1:3001
```

```powershell
npm run dev # Boutique http://127.0.0.1:5174
```

Les ports dev sont fixes, sans conflit avec ReleasePilot. Vite dev et preview proxient `/api` vers le BFF, aucune configuration CORS large. `npm run build` puis `npm run preview` permettent aussi de tester le bundle sur 5174, avec le BFF toujours lance. Le mode Docker final sert la SPA via Nginx et proxie /api vers le container BFF prive : `docker compose --profile full up --build -d --wait --wait-timeout 180` depuis la racine. Boutique Docker : http://127.0.0.1:15174 ; dashboard Docker : http://127.0.0.1:15173. Aucun secret au build. DEMO_BFF_HOST permet l'ecoute inter-container, loopback reste le defaut local. Voir [commands.md](../docs/commands.md).

## Preparer ReleasePilot

Dans le dashboard : Register/Login, creer **Demo Store**, selectionner **Production**, creer le flag de key **new_checkout**, puis generer une API key Production. Copier le secret au moment de sa creation dans le `.env` serveur. Redemarrer le BFF si ses variables changent ; modifier un flag ne demande aucun redemarrage.

Option pour une fixture dediee, avec backend actif et **sans `.env` existant** : `npm run setup`. Ce script cree un compte local de demonstration, son Project Demo Store, Production, new_checkout et sa cle via les API publiques. Il conserve volontairement ces ressources pour les demos suivantes ; aucune donnee existante n'est modifiee. Il ecrit la cle dans `.env` et les credentials de ce compte dans `.local/fixture.json`, tous deux ignores, sans imprimer de secret. Utiliser les credentials de ce fichier pour piloter ce projet dans le dashboard. Le script refuse d'ecraser un `.env` existant. Ne pas executer setup sur un environnement public.

## Parcours et demonstration

1. `/` : ajouter des produits fictifs ; `/cart` : ajuster les quantites, continuer vers `/checkout`.
2. Production **OFF** : Classic checkout / V1 (sections classiques, resume lateral).
3. Production **ON / 30%** : cliquer **Re-evaluate**, puis **Generate another visitor**. Certains visiteurs voient V1 (`ROLLOUT_MISS`), d'autres V2 (`ROLLOUT_MATCH`). Un petit echantillon n'est pas necessairement exactement 30%.
4. Production **ON / 100%** : Re-evaluate donne New checkout / V2 (formulaire compact, resume sticky) pour tout visiteur.
5. Production **OFF** : Re-evaluate revient a V1, sans modifier, rebuilder ni redeployer Demo Store.

`crypto.randomUUID()` cree `demo-user-<uuid>`, conserve dans `localStorage` sous `morrow.visitor`. Le visitor reste stable au refresh ; le bouton de demonstration le remplace volontairement. Si le stockage est bloque, le visitor reste en memoire pour la page courante. Le panier est aussi local ; aucun secret ou champ de formulaire n'est persiste. Le navigateur ne recalcule pas le rollout : ReleasePilot reste l'unique decideur.

Au chargement checkout et a chaque Re-evaluate/changement de visitor, le navigateur appelle le BFF. `enabled=true` selectionne V2 ; `false` selectionne V1. Le panneau de demonstration affiche visitor, flag, ENABLED/DISABLED et reason, sans cle. Loading explicite, requetes obsoletees ignorees, bouton verrouille pendant l'evaluation.

## Fail-safe

ReleasePilot indisponible, timeout, cle invalide/revoquee, HTTP non-2xx ou JSON invalide : **safe fallback → Checkout V1**. Le BFF retourne HTTP 200 et `{flagKey,enabled:false,reason:"RELEASEPILOT_UNAVAILABLE"}`. Ce reason est propre a Demo Store, pas un reason code invente dans ReleasePilot. Si le BFF lui-meme est inaccessible, le frontend applique egalement V1 apres un timeout de 5 secondes. Message discret, formulaire utilisable, commande de demonstration sans aucun vrai paiement. `FLAG_NOT_FOUND` est une decision ReleasePilot valide false, affichee telle quelle.

## API BFF et validation

`POST /api/feature-evaluation`, Content-Type application/json, accepte uniquement `{ "userKey": "demo-user-..." }`, non vide, maximum 256 caracteres, corps maximum 4 KiB. Le flag et le secret sont exclusivement serveur. Reponse publique : `{ "flagKey": "new_checkout", "enabled": true, "reason": "ROLLOUT_MATCH" }`, `Cache-Control: no-store`. `GET /api/health` verifie le BFF uniquement, pas la disponibilite de ReleasePilot.

```powershell
npm run build
npm test
npm run smoke # fixture npm run setup, BFF et Vite actifs
```

15 tests Node ciblent le header serveur, les projections sans secret, userKey, V1/V2, erreur HTTP/reseau/JSON, timeout HTTP reel, validation BFF, visitor stable/nouveau, fallback navigateur et total du panier.

Le smoke teste les vrais appels via **Vite ou Nginx → BFF → ReleasePilot** : Login/Project, OFF, 100% (12 visiteurs), 30% (40 visiteurs, chaque decision repetee), OFF a nouveau, Audit et API key invalide sur un BFF ephemere. Il modifie uniquement la fixture dediee et restaure sa configuration dans `finally`. Les credentials et IDs sont lus depuis `.local/fixture.json`, jamais imprimes. DEMO_STORE_ORIGIN permet de choisir la boutique Docker (15174) ; RELEASEPILOT_BASE_URL peut etre surchargee par le processus vers 18080. Voir les commandes completes dans commands.md. Pour un projet configure manuellement, suivre le parcours dashboard ci-dessus plutot que ce script de fixture.

Validation Phase 5 : build OK, 15/15 tests ; smoke HTTP 30% : 12 V2 / 28 V1, 40/40 stables. Chrome reel avec sandbox activee : storefront/panier/checkout, OFF/V1, 100%/V2, 30% avec deux decisions et stabilite, refresh du visitor, retour OFF, message fallback et commande demo, responsive 390 px V1/V2 sans debordement, aucune erreur JavaScript. La cle invalide a ete verifiee avec ReleasePilot reel par le smoke HTTP ; l'affichage fallback a ete force dans le navigateur. Etat detaille dans [CURRENT_STATE.md](../CURRENT_STATE.md).
