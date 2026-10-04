# Product Frontend — Phase 4

React + TypeScript strict + Vite. Interface en anglais, palette neutre sombre avec accent vert, sidebar, topbar, cards, tables, badges et notifications. Desktop prioritaire ; grilles adaptatives et navigation compacte sur petits ecrans. Aucun changement de logique metier backend.

## Lancement

Depuis la racine, terminal backend :

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
. ./scripts/load-local-env.ps1
docker compose up -d --wait --wait-timeout 60
cd backend
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

Dans un autre terminal :

```powershell
cd frontend
# Seulement lors de la premiere installation ou si node_modules est absent :
npm install
# Facultatif : copier .env.example vers .env si ce fichier n'existe pas.
npm run build
npm test
npm run dev
```

Ouvrir **http://127.0.0.1:5173**. Le script dev utilise strictPort : si 5173 est occupe, il echoue plutot que changer silencieusement d'origine. Un serveur Vite deja lance sur ce workspace peut etre reutilise.

## Configuration et CORS

`frontend/.env.example` : `VITE_API_BASE_URL=http://localhost:8080`. La configuration est lue uniquement par le client HTTP, jamais hardcodee dans les composants. Les variables VITE_ sont publiques et integrees au bundle : aucun secret ne doit y figurer. Relancer Vite apres changement de configuration ; reconstruire le bundle pour une autre URL de production.

Backend : `FRONTEND_ORIGIN=http://127.0.0.1:5173` par defaut, une origine HTTP(S) exacte sans chemin ni slash final. Configurer cette variable pour changer l'origine autorisee puis redemarrer Spring Boot. localhost et 127.0.0.1 sont des origines differentes. Le dashboard utilise uniquement Bearer, sans cookies : credentials omit cote fetch, allowCredentials=false cote CORS.

La chaine Security dashboard traite CORS avant l'authentification. Methodes GET/POST/PUT/PATCH/DELETE/OPTIONS, headers Authorization/Content-Type/Accept. Aucun wildcard. Le preflight ne donne aucun droit d'acces : les requetes dashboard sans JWT restent 401. La chaine Evaluation/API key n'a pas ete ouverte au navigateur ; ce frontend ne l'appelle pas. Les applications clientes continuent d'utiliser leur Environment API key cote serveur.

Mode developpement : Vite sert la SPA. Mode Docker final : Nginx non-root sert index.html sur les routes du dashboard et proxie /api vers le backend. Le bundle Docker utilise une base URL vide (meme origine) ; les .env locaux sont exclus du build. Voir [commands.md](commands.md).

## Structure

```text
frontend/
├── .env.example
├── src/
│   ├── api/           # client HTTP, erreurs, appels par domaine
│   ├── auth/          # AuthProvider, session et garde de routes
│   ├── components/    # modal, confirmation, switch, formulaires, feedback, toasts, icones
│   ├── features/
│   │   ├── projects/
│   │   ├── environments/
│   │   ├── flags/
│   │   ├── apiKeys/
│   │   └── audit/
│   ├── hooks/         # chargement annulable, erreurs, retry
│   ├── layouts/       # sidebar et topbar du workspace
│   ├── pages/         # Auth, Dashboard, Projects, Project, Account
│   ├── types/         # DTOs correspondant aux vrais records Java
│   ├── utils/         # navigation Browser History et formatage
│   ├── App.tsx
│   ├── main.tsx
│   └── style.css
└── tests/             # Node test runner : client, session/garde, rollout
```

Pas de nouvelle dependance. Navigation pour les routes finies du MVP via Browser History, liens interceptes uniquement pour clic gauche interne sans modificateur, back/forward via popstate et useSyncExternalStore. Les liens restent de vrais anchors. Les formulaires, dialogues natifs, switch et sliders restent accessibles au clavier ; focus visible et skip link.

## Routes

| Route | Contenu |
|---|---|
| /login | Connexion |
| /register | Creation de compte puis retour Login |
| /dashboard | Nombre reel de Projects, six derniers Projects au maximum, empty state |
| /projects | Liste et recherche locale, creation |
| /projects/:projectId | Overview, Environments, edition/suppression Project |
| /projects/:projectId/flags | Liste, recherche et creation de Feature Flags |
| /projects/:projectId/flags/:flagId | Edition/suppression Flag, ON/OFF et rollout par Environment |
| /projects/:projectId/api-keys | Selection Environment, generation/liste/revocation |
| /projects/:projectId/audit | Audit pagine, 20 evenements par page |
| /account | Identite du compte courant et logout |

Les routes dashboard exigent une session restauree. / redirige vers Dashboard ou Login selon l'etat Auth. Page inconnue : empty state 404 frontend. Aucun endpoint d'edition de profil n'existe : la page Account presente les donnees reelles et le logout, sans inventer d'operation.

## Auth et client HTTP

Register retourne UserResponse ; aucun token emis par Register. Login obtient AuthResponse, stocke uniquement le JWT dans sessionStorage puis appelle /api/v1/auth/me pour charger l'identite courante. Au demarrage, un token existant impose un nouvel appel /me avant tout affichage dashboard. Un 401 de requete dashboard efface la session et redirige Login. Un 401 de Login public reste une erreur de credentials sur le formulaire. Une erreur reseau de restauration affiche retry/retour Login, sans supprimer silencieusement un token potentiellement valide.

Un timer base sur exp facilite la sortie de session a expiration ; il n'est pas une verification de signature. Le serveur reste l'autorite sur le JWT et /me. Logout efface le token du navigateur ; la validite residuelle d'un JWT emis jusqu'a son expiration reste celle du MVP backend. Aucun token dans localStorage, URL ou logs. sessionStorage reste accessible au JavaScript de l'origine : prevenir les injections reste necessaire, et ce MVP ne rend aucun HTML utilisateur via dangerouslySetInnerHTML.

Le client central prend en charge base URL, JSON, Bearer, 204, ApiError/fieldErrors, abort des lectures et erreurs reseau. Les erreurs serveur 5xx sont remplacees par un message generique, sans details internes. Une reponse 401 tardive d'une ancienne session n'efface pas une nouvelle session. DTOs de sortie sans passwordHash, hash d'API key ou secret interne ; metadata audit typees unknown et affichage uniquement des champs attendus.

## Comportements produit

- Projects : name/key/description a la creation ; key permanente en lecture seule a l'edition. Creation redirige Overview, suppression confirmee redirige Projects. Aucun formulaire Environment CRUD sans support backend.
- Flags : creation redirige vers les configurations ; valeurs serveur initiales OFF/0. Edition name/description seulement, suppression confirmee.
- Switch : requete immediate, verrou local pendant sauvegarde, etat ON uniquement apres confirmation serveur ; erreurs visibles, etat confirme conserve.
- Rollout : draft local pendant drag, PATCH au pointerup, fin d'action clavier ou blur. Verrou empechant les requetes concurrentes/double commit ; reponse serveur resynchronise ; echec retablit le dernier pourcentage confirme. OFF conserve son pourcentage mais bloque l'evaluation.
- API keys : liste de metadata uniquement. Secret garde uniquement dans l'etat ephemere de la modal de creation, copie via Clipboard API, suppression de cet etat a fermeture ou sortie de la section/Environment. Aucun stockage navigateur du secret. La copie choisie par l'utilisateur laisse naturellement la valeur dans son presse-papiers. Si l'acces au clipboard echoue, message et selection manuelle restent disponibles.
- Audit : labels humains, acteur identifie par le User courant lorsqu'il correspond a actorUserId, sinon identifiant reel ; timestamps, noms/keys disponibles et changements ON/OFF/rollout. Pas de nom d'acteur invente. L'audit d'un Project supprime reste conserve par le backend ; la navigation courante liste les Projects existants.
- Dashboard : un seul appel Projects, aucun total de flags global invente ni N+1 d'agregation. Le nombre de flags d'un Project est charge sur son Overview.

## Validation et smoke manuel

`npm run build` verifie TypeScript strict puis produit dist. `npm test` utilise le runner Node 22 existant, sans Vitest/Jest additionnel ; couvre session/garde, expiration, erreurs HTTP, 401, absence de cookies, 204 et validation du commit rollout. Aucun script lint n'existait et aucun n'a ete ajoute. Trois tests d'integration backend couvrent CORS avec les vrais services.

Parcours a verifier dans le navigateur local :

1. Register puis Login ; nouvel utilisateur voit le Dashboard sans Projects.
2. Create Project : Demo Store / demo-store ; constater Development, Staging, Production.
3. Feature flags : Create flag new_checkout ; constater les trois configurations OFF/0.
4. Activer Production ; verifier ON apres sauvegarde ; regler rollout a 30% et recharger pour confirmer la persistance.
5. API keys : selectionner Production, generer une cle, copier ; fermer la modal, verifier que le secret complet ne reapparait pas dans la liste.
6. Audit : consulter les actions et changements ; tester la pagination si plus de 20 evenements.
7. Tester edition Project/Flag, confirmations de suppression et revocation de cle.
8. Logout ; acces direct /dashboard renvoie Login. Reconnexion/restauration avec un token present ; token expire/invalide renvoie Login.
9. Verifier clavier/focus et layout tablette, absence d'erreurs console.

Les resultats reellement executes et les limites de la session sont dans [CURRENT_STATE.md](../CURRENT_STATE.md). Le controle HTTP direct ne remplace pas ce smoke UI, notamment les interactions, le clipboard et l'inspection visuelle.

References techniques : [React useSyncExternalStore](https://react.dev/reference/react/useSyncExternalStore), [Spring Security CORS](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html).
