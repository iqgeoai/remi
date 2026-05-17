# Stage 4a — Frontend Ionic/Angular: Auth + Lobby + WS + Game Placeholder (Design)

**Data:** 2026-05-17
**Status:** Approved
**Scope:** Stage 4a — sub-faza 1 din Stage 4 originar. Game UI complet (port din `assets/remi.html`) este Stage 4b cu spec propriu.

## Context

Stage 1+2+3 au livrat backend complet: engine, auth (JWT + refresh + email verify + password reset), multiplayer (lobby + matchmaking + WebSocket cu STOMP+SockJS + per-user broadcast). Toate REST + WS contracts sunt stabile (vezi `2026-05-16-stage1`, `2026-05-16-stage2`, `2026-05-17-stage3` design docs).

Stage 4a livrează frontend-ul **minim end-to-end jucabil**: register/login/lobby/quick-match cu WebSocket conectat și un placeholder de game care arată state-ul ca JSON și permite trimiterea de Action-uri prin formular. Stage 4b va înlocui placeholder-ul cu UI real (drag&drop, melduri, animații, sound).

## Cerințe (confirmate cu user)

- **Locație repo**: sub-folder `frontend/` în repo-ul existent (același `.git`)
- **State management**: **NgRx** (alegere acceptată după mini-push-back pentru Signals)
- **Styling**: **Ionic native UI + SCSS** per component (fără Tailwind)
- **Test framework**: **Karma + Jasmine** (default Angular CLI; notă: Karma e deprecated official — candidate de migrare la Jest în Stage 4b)
- **Token storage**: `localStorage` în Stage 4a, `Capacitor Preferences` în Stage 5 (abstractizat via `AuthStorageService`)
- **Game UI 4a**: minimal debug — JSON pretty-print + form submit Action

## Stack tehnic

- Ionic 8 + Angular 19 (latest stable)
- Capacitor 6 (instalat dar build mobile vine în Stage 5)
- TypeScript strict mode
- Standalone components (Angular 17+ default)
- NgRx 18 (store + effects + entity)
- `@stomp/stompjs` + `sockjs-client` pentru WebSocket
- Karma + Jasmine (default Angular CLI)

## 1. Arhitectură

```
frontend/
  src/
    app/
      core/
        api/           ← HttpClient services (AuthApi, LobbyApi, MatchmakingApi)
        ws/            ← StompService, GameWsService
        auth/          ← AuthStorageService, jwt.interceptor, auth.guard
        models/        ← TypeScript types (User, LobbyGame, GameView, Action, AuthTokens)
        i18n/          ← ERROR_MESSAGES map + localizeError pipe
      store/
        auth/          ← actions, reducer, effects, selectors
        lobby/         ← idem
        match/         ← idem
        game/          ← idem
      features/
        auth/          ← LoginPage, RegisterPage, VerifyEmailPage,
                         RequestResetPage, ResetPasswordPage
        lobby/         ← LobbyHomePage, CreateGamePage, JoinByCodePage,
                         PublicListPage, QuickMatchPage
        game/          ← GameDebugPage (4a placeholder)
      shared/          ← ErrorBanner, WsIndicator, GlobalErrorHandler
      app.routes.ts    ← Standalone routing
      app.config.ts    ← Bootstrap providers
    environments/
    main.ts
  ionic.config.json
  angular.json
  capacitor.config.ts
  package.json
  tsconfig.json
```

**Regulă cheie:** `core/api` și `core/ws` au INTERFAȚĂ pură către NgRx effects. Componentele consumă DOAR signals/selectors NgRx (niciun HttpClient direct dintr-un component).

## 2. Componente și API-uri publice

### 2.1 Models (TypeScript types)

Reflect 1:1 cu DTO-urile backend:

```typescript
// core/models/auth.model.ts
export interface User {
  id: string; email: string; username: string;
  emailVerified: boolean; createdAt: string;
}
export interface AuthTokens {
  accessToken: string; refreshToken: string; accessExpiresAt: string;
}

// core/models/lobby.model.ts
export type GameVisibility = 'PRIVATE' | 'PUBLIC';
export type Mode = 'ETALAT' | 'TABLA';
export type Difficulty = 'EASY' | 'MED' | 'HARD';
export interface LobbyGame {
  id: string; ownerId: string; visibility: GameVisibility; joinCode: string | null;
  numPlayers: number; mode: Mode; difficulty: Difficulty;
  seatsTaken: number; started: boolean; createdAt: string;
}

// core/models/game.model.ts
export interface Piece { id: number; num: number; color: 'RED'|'YELLOW'|'BLUE'|'BLACK'|'JOKER'; isJoker: boolean; }
export interface PlayerView { name: string; isBot: boolean; hasEtalat: boolean; calledAtu: boolean;
  announced: boolean; mustUsePieceId: number | null; hand: Piece[]; handCount: number; }
export interface Meld { owner: number; type: 'GROUP'|'SUITE'; pieces: Piece[]; placedBy: Record<number, number>; }
export interface GameView { id: string; players: PlayerView[]; stockCount: number; discard: Piece[];
  atu: Piece; melds: Meld[]; current: number; phase: 'DRAW'|'ACTION'|'DISCARD';
  drewFrom: 'STOCK'|'DISCARD'|null; turnTaken: number; round: number;
  mode: Mode; difficulty: Difficulty; doubleGame: boolean; closed: boolean; totals: number[]; }

// core/models/action.model.ts
export type Action =
  | { type: 'DRAW_FROM_STOCK'; playerIdx: number }
  | { type: 'TAKE_DISCARD'; playerIdx: number; discardIdx: number }
  | { type: 'ETALAT'; playerIdx: number; melds: { type: 'GROUP'|'SUITE'; pieceIds: number[] }[] }
  | { type: 'LAYOFF'; playerIdx: number; layoffs: { pieceId: number; meldIdx: number }[] }
  | { type: 'DISCARD'; playerIdx: number; pieceId: number }
  | { type: 'FORCE_AUTO'; playerIdx: number };

export interface ApiError { code: string; message: string; }
```

### 2.2 API services

```typescript
export class AuthApi {
  register(req): Observable<User>;
  verifyEmail(token: string): Observable<void>;
  login(req): Observable<AuthTokens>;
  refresh(refreshToken: string): Observable<AuthTokens>;
  logout(refreshToken: string): Observable<void>;
  requestPasswordReset(email: string): Observable<void>;
  resetPassword(token: string, newPassword: string): Observable<void>;
  me(): Observable<User>;
}

export class LobbyApi {
  create(req): Observable<LobbyGame>;
  joinPublic(gameId: string): Observable<LobbyGame>;
  joinByCode(joinCode: string): Observable<LobbyGame>;
  listPublic(): Observable<LobbyGame[]>;
  myGames(): Observable<LobbyGame[]>;
  get(gameId: string): Observable<GameView>;
  apply(gameId: string, action: Action): Observable<GameView>;
  leave(gameId: string): Observable<void>;
}

export class MatchmakingApi {
  quick(req): Observable<{matched: boolean, game?: LobbyGame}>;
  cancel(): Observable<void>;
}
```

### 2.3 Auth: storage + interceptor + guard

```typescript
export class AuthStorageService {
  setTokens(tokens: AuthTokens): void;       // 4a: localStorage; 5: Capacitor Preferences
  getTokens(): AuthTokens | null;
  clear(): void;
}

// HttpInterceptorFn (Angular 19 functional)
export const jwtInterceptor: HttpInterceptorFn = (req, next) => { ... };

// CanActivateFn
export const authGuard: CanActivateFn = () => { ... };
```

### 2.4 WebSocket

```typescript
export class StompService {
  connect(accessToken: string): Observable<ConnectionState>;
  disconnect(): void;
  subscribe<T>(destination: string, deserializer?: (raw: string) => T): Observable<T>;
  send(destination: string, payload: unknown): void;
}

export class GameWsService {
  subscribeToGame(gameId: string): Observable<{view: GameView, events: any[]}>;
  subscribeToErrors(): Observable<ApiError>;
  subscribeToMatches(): Observable<LobbyGame>;
  sendAction(gameId: string, action: Action): void;
}
```

`StompService` păstrează un `Map<destination, Subject<T>>` ca re-emit-ul după reconnect să fie transparent.

### 2.5 NgRx structure

4 features × 5 fișiere fiecare = 20 fișiere:

```
store/auth/        ← loginRequested/Succeeded/Failed, refresh, logout, bootstrap, sessionInvalidated
store/lobby/       ← list+filter, create, join, leave
store/match/       ← quick-match (idle | queued | matched | error)
store/game/        ← currentGameId, currentGameView, events log, status
```

Fiecare feature: `*.actions.ts`, `*.reducer.ts`, `*.effects.ts`, `*.selectors.ts`, `*.feature.ts` (createFeature wrapper).

### 2.6 Routing

```typescript
export const routes: Routes = [
  { path: '', redirectTo: 'lobby', pathMatch: 'full' },
  { path: 'login', loadComponent: () => import('./features/auth/login.page') },
  { path: 'register', loadComponent: () => import('./features/auth/register.page') },
  { path: 'verify', loadComponent: () => import('./features/auth/verify-email.page') },
  { path: 'reset/request', loadComponent: () => import('./features/auth/request-reset.page') },
  { path: 'reset/confirm', loadComponent: () => import('./features/auth/reset-password.page') },
  {
    path: 'lobby', canActivate: [authGuard],
    children: [
      { path: '', loadComponent: () => import('./features/lobby/lobby-home.page') },
      { path: 'create', loadComponent: () => import('./features/lobby/create-game.page') },
      { path: 'join', loadComponent: () => import('./features/lobby/join-by-code.page') },
      { path: 'public', loadComponent: () => import('./features/lobby/public-list.page') },
      { path: 'quick', loadComponent: () => import('./features/lobby/quick-match.page') },
    ],
  },
  { path: 'game/:id', canActivate: [authGuard],
    loadComponent: () => import('./features/game/game-debug.page') },
  { path: '**', redirectTo: 'lobby' },
];
```

### 2.7 Environments

```typescript
// environments/environment.ts (dev)
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',
  wsUrl: 'http://localhost:8080/ws',
};
```

Proxy via `proxy.conf.json` pentru a evita CORS în dev.

## 3. Data flow

### 3.1 Bootstrap

`main.ts` → `bootstrapApplication(AppComponent, appConfig)` cu providers (router, http+interceptors, store, effects, Ionic).
`AppComponent.ngOnInit` → `store.dispatch(Auth.bootstrapFromStorage())` care încearcă restoration cu refresh dacă access expirat.

### 3.2 Login

`LoginPage` → form submit → `Auth.loginRequested` → `AuthEffects.login$` → `AuthApi.login` → success → `Auth.loginSucceeded({tokens})` → effect separat persistă în storage + `AuthApi.me()` → `Auth.userLoaded` → navigate `/lobby`.

### 3.3 Create private game

`CreateGamePage` form → `Lobby.createRequested` → `LobbyApi.create` → `Lobby.createSucceeded({game})` → dispatch `Game.subscribeToGame({gameId})` + navigate `/game/:id`.

`GameEffects.subscribeToGame$`: `gameWsService.subscribeToGame(gameId).subscribe(... → Game.viewReceived(...))`.

### 3.4 Join by code

Identic ca 3.3 dar `joinByCode` în loc de `create`.

### 3.5 Quick-match

`QuickMatchPage` activează la component init un effect `subscribeToMatchTopicOnEnter$` care subscribe la `/user/queue/match` **înainte** de a apela `/api/matchmaking/quick`. Critical: previne pierderea notificării când match-ul vine în background.

Form submit → `Match.quickRequested` → `MatchEffects.quick$` → `MatchmakingApi.quick`. Response:
- `{matched: true, game}` → `Match.matched({game})` → subscribe game + navigate
- `{matched: false}` → `Match.queued` → UI "În așteptare..." + buton cancel
- Background WS notification → `Match.matched({game})` (din subscription) → navigate

### 3.6 In-game action via WS

`GameDebugPage` form → `Game.sendAction({gameId, action})` → `GameEffects.sendAction$` → `gameWsService.sendAction(gameId, action)` (STOMP SEND, no response).

Server broadcasts back → `Game.viewReceived({view, events})` (din subscription) → reducer update.

### 3.7 WS reconnect

`StompService` exponential backoff: 1s, 2s, 4s, ..., max 30s. State `RECONNECTING` afișat în `WsIndicator`. Active subscriptions re-emit transparent după reconnect.

### 3.8 Logout

`Auth.logoutRequested` → `AuthApi.logout(refreshToken)` + `authStorage.clear()` + `stomp.disconnect()` + navigate `/login`. `Auth.logoutLocal` (no API call) folosit pentru session-invalidation paths.

## 4. Error handling

### Layer 1 — `JwtInterceptor`

- Adăugă `Authorization: Bearer` pe orice request dacă tokens prezenți
- 401 `TOKEN_EXPIRED` → refresh + retry (cu deduplication via shared `refreshInFlight$: ReplaySubject`)
- 401 `TOKEN_REUSED` sau alte → `Auth.sessionInvalidated({reason})` → clear storage + route `/login` + banner prominent

Edge cases acoperite:
- Concurrent 401 → single refresh call
- Refresh endpoint însuși 401 → no infinite loop (excludem `/api/auth/refresh` din retry path)

### Layer 2 — NgRx effects

Toate API calls în effects sunt wrapped cu `catchError → *.failed({error: ApiError})`. Codurile backend ajung intacte la reducer.

### Layer 3 — UI feedback

- `<app-error-banner [error]="error()">` pe forme auth/lobby
- Ionic `ToastController` pentru erori in-game prin WS (auto-dismiss 4s)
- Field-level errors pe forme (`EMAIL_TAKEN` → highlight pe email field)
- `<app-ws-indicator>` global pentru connection state

### Localization

`core/i18n/errors.ts` map backend code → Romanian message. Toate codurile Stage 2+3 acoperite. Codurile frontend-only: `NETWORK`, `UNAUTHORIZED`, `WS_DISCONNECTED`, `UNKNOWN`.

```typescript
export function localizeError(error: ApiError): string {
  return ERROR_MESSAGES[error.code] ?? error.message ?? ERROR_MESSAGES['UNKNOWN'];
}
```

Folosit ca `{{ error | errorMessage }}` într-un pipe.

### Global error handler

`GlobalErrorHandler implements ErrorHandler` catch-all → toast roșu "Eroare neașteptată" + console.error. Sentry/equivalent deferat Stage 8.

### Logging

- `console.error`: doar excepții infrastructure
- `console.warn`: erori user (disabled în prod)
- **Niciodată logate**: parole, JWT-uri, refresh tokens, hand-uri

## 5. Testing

### Piramidă

```
E2E browser (deferat Stage 5)                ~5 teste
Component tests (Angular TestBed)            ~30 teste
NgRx unit (reducer + selectors pure)         ~20 teste
Service unit (api wrappers, interceptor, ws) ~20 teste
```

### Service unit tests (cu HttpTestingController + mocks)

- `AuthApiTest`, `LobbyApiTest`, `MatchmakingApiTest`: URL/method/body/response/error per method
- `AuthStorageServiceTest`: set/get/clear, malformed JSON → null
- `JwtInterceptorTest`: header injection, 401 TOKEN_EXPIRED → refresh+retry, concurrent dedup, refresh fail no-loop
- `StompServiceTest` (cu stompjs mock): connect headers, subscribe, send, reconnect re-emits active subs
- `GameWsServiceTest`: destination routing, payload serialization

### NgRx unit tests

- `AuthReducerTest`: happy paths + error paths per action
- `AuthSelectorsTest`: pure functions
- `LobbyReducerTest`, `GameReducerTest`, `MatchReducerTest`: same pattern
- `AuthEffectsTest` (cu `provideMockActions` + service mocks): success/fail per action chain, bootstrap with refresh edge cases
- `LobbyEffectsTest`, `MatchEffectsTest`, `GameEffectsTest`: similar

### Component tests

- `LoginPageTest`: form render, submit dispatch, error banner, field-level errors, loading state
- `RegisterPageTest`, `VerifyEmailPageTest`, `RequestResetPageTest`, `ResetPasswordPageTest`: similar
- `CreateGamePageTest`: form render, submit dispatch, navigate on success
- `JoinByCodePageTest`, `PublicListPageTest`, `QuickMatchPageTest`: similar
- `LobbyHomePageTest`: 4 navigation links
- `GameDebugPageTest`: JSON pretty-print, action form (conditional inputs), submit dispatch, toast on error
- `AppComponentTest`: bootstrap dispatch, WS indicator, logout button

### Manual smoke test (înlocuiește E2E)

`frontend/SMOKE_TEST.md` documentează pas-cu-pas:
1. Backend cu Postgres pornit
2. `npm start` în /frontend
3. Register user A → verify (link din log backend) → login
4. Create private game → land în /game/:id, JSON state
5. Register user B în alt browser → verify → login → join by code
6. Ambii văd state updates când A submite action
7. Logout → redirect /login

### Coverage

- Karma + Istanbul reporter configurat
- Target inițial 70% (services 80%, components 60%, reducers 90%)
- NU gate enforced în CI pentru 4a (baseline trebuie stabilit); stabilim gate în 4b sau retrospective

### Test infrastructure

```typescript
// src/test-utils/store-test-bed.ts
export function configureStoreTestBed(initialState?: Partial<AppState>) { ... }

// src/test-utils/test-data.ts
export const TEST_USER: User = {...};
export const TEST_LOBBY_GAME: LobbyGame = {...};
export const TEST_GAME_VIEW: GameView = {...};
```

## Excluderi explicite (deferate)

- **Game UI complet** (drag&drop, pieces, melduri, animații, sound) → Stage 4b
- **Mobile build** (iOS + Android via Capacitor) → Stage 5
- **E2E în browser real** (Cypress/Playwright) → Stage 5
- **Migrare Karma → Jest** → Stage 4b refactor candidate
- **Sentry / observability** → Stage 8
- **i18n complet** (Stage 4a are doar Romanian hard-coded; multi-language deferat)
- **PWA features** (service worker, offline mode) → Stage 5+
