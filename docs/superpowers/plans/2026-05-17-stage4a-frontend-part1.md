# Stage 4a — Frontend Implementation Plan (Part 1: Scaffold + Core utilities)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scaffold an Ionic 8 + Angular 19 + NgRx 18 frontend under `/frontend`, with all core utilities (models, storage, error localization, shared UI components) ready for API/WS/feature work in Parts 2-3.

**Architecture:** Standalone-component Angular app inside the existing Java monorepo. NgRx for state management. Native Ionic UI + SCSS. Karma+Jasmine tests. `/frontend` is a separate npm project — the existing `pom.xml` and Java sources are untouched.

**Tech Stack:** Ionic 8, Angular 19 (standalone, esbuild), NgRx 18, @stomp/stompjs, sockjs-client, Capacitor 6 (installed but not built in 4a), TypeScript strict, Karma+Jasmine, RxJS 7+.

---

## File Structure (final, after all 3 parts)

```
frontend/
  src/
    app/
      core/
        api/         AuthApi, LobbyApi, MatchmakingApi
        ws/          StompService, GameWsService
        auth/        AuthStorageService, jwt.interceptor, auth.guard
        models/      auth/lobby/game/action.model.ts
        i18n/        errors.ts, error-message.pipe.ts
      store/
        auth/        actions, reducer, effects, selectors, feature
        lobby/       idem
        match/       idem
        game/        idem
      features/
        auth/        login, register, verify-email, request-reset, reset-password
        lobby/       lobby-home, create-game, join-by-code, public-list, quick-match
        game/        game-debug
      shared/        error-banner, ws-indicator, global-error-handler
      app.routes.ts
      app.config.ts
      app.component.ts
    environments/
    test-utils/
    main.ts
  ionic.config.json
  angular.json
  capacitor.config.ts
  package.json
  tsconfig.json
  proxy.conf.json
  SMOKE_TEST.md
```

---

## Phase A — Scaffold

### Task A1: Scaffold Ionic + Angular project

**Files:**
- Create: `/frontend/` (entire scaffold via `ionic start`)
- Modify: root `.gitignore`

- [ ] **Step 1: Run Ionic CLI scaffold** (requires `npm install -g @ionic/cli` if missing)

From `/Users/georgesand/IdeaProjects/remi/`:

```bash
ionic start frontend blank --type=angular --capacitor --no-git --no-link
```

This creates `frontend/` with Angular 19 standalone + Ionic 8 + Capacitor 6.

- [ ] **Step 2: Verify the app builds**

```bash
cd frontend
npm install
npx ng build --configuration=development
```

Expected: SUCCESS, output in `frontend/www/`.

- [ ] **Step 3: Add `/frontend/` artifacts to root `.gitignore`**

At repo root `/Users/georgesand/IdeaProjects/remi/.gitignore` (create if missing):

```
# Frontend
frontend/node_modules/
frontend/www/
frontend/.angular/
frontend/dist/
frontend/coverage/
frontend/ios/
frontend/android/
```

- [ ] **Step 4: Commit**

```bash
git add frontend .gitignore
git commit -m "feat(frontend): scaffold Ionic 8 + Angular 19 + Capacitor 6 in /frontend"
```

---

### Task A2: TypeScript strict + environment files

**Files:**
- Modify: `frontend/tsconfig.json`
- Create: `frontend/src/environments/environment.ts`
- Create: `frontend/src/environments/environment.prod.ts`
- Modify: `frontend/angular.json` (add fileReplacements)

- [ ] **Step 1: Enable TypeScript strict in `frontend/tsconfig.json`**

Replace the `compilerOptions` block (or merge):

```json
{
  "compileOnSave": false,
  "compilerOptions": {
    "outDir": "./dist/out-tsc",
    "strict": true,
    "noImplicitOverride": true,
    "noPropertyAccessFromIndexSignature": true,
    "noImplicitReturns": true,
    "noFallthroughCasesInSwitch": true,
    "skipLibCheck": true,
    "isolatedModules": true,
    "esModuleInterop": true,
    "experimentalDecorators": true,
    "moduleResolution": "bundler",
    "importHelpers": true,
    "target": "ES2022",
    "module": "ES2022",
    "useDefineForClassFields": false,
    "lib": ["ES2022", "dom"]
  },
  "angularCompilerOptions": {
    "enableI18nLegacyMessageIdFormat": false,
    "strictInjectionParameters": true,
    "strictInputAccessModifiers": true,
    "strictTemplates": true
  }
}
```

- [ ] **Step 2: Create `frontend/src/environments/environment.ts`**

```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',
  wsUrl: 'http://localhost:8080/ws',
};
```

- [ ] **Step 3: Create `frontend/src/environments/environment.prod.ts`**

```typescript
export const environment = {
  production: true,
  apiUrl: '/api',
  wsUrl: '/ws',
};
```

- [ ] **Step 4: Wire fileReplacements in `frontend/angular.json`**

Inside `projects.app.architect.build.configurations.production`, add (or merge into existing):

```json
"fileReplacements": [
  {
    "replace": "src/environments/environment.ts",
    "with": "src/environments/environment.prod.ts"
  }
]
```

- [ ] **Step 5: Verify build still works**

```bash
cd frontend && npx ng build --configuration=production
```

Expected: SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add frontend/tsconfig.json frontend/src/environments/ frontend/angular.json
git commit -m "build(frontend): TypeScript strict mode + environment files (dev/prod)"
```

---

### Task A3: Dev proxy to backend

**Files:**
- Create: `frontend/proxy.conf.json`
- Modify: `frontend/angular.json` (serve options)

- [ ] **Step 1: Write `frontend/proxy.conf.json`**

```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true,
    "logLevel": "debug"
  },
  "/ws": {
    "target": "http://localhost:8080",
    "secure": false,
    "ws": true,
    "changeOrigin": true,
    "logLevel": "debug"
  }
}
```

- [ ] **Step 2: Wire proxy in `frontend/angular.json`**

Inside `projects.app.architect.serve.configurations.development`, add:

```json
"proxyConfig": "proxy.conf.json"
```

(Or merge with existing development configuration.)

- [ ] **Step 3: Update `environment.ts` to use relative paths via proxy** (so dev and prod behave identically through proxy/origin)

Update `frontend/src/environments/environment.ts`:

```typescript
export const environment = {
  production: false,
  apiUrl: '/api',
  wsUrl: '/ws',
};
```

(Now both dev and prod use relative paths; dev hits the proxy, prod hits the same origin.)

- [ ] **Step 4: Commit**

```bash
git add frontend/proxy.conf.json frontend/angular.json frontend/src/environments/environment.ts
git commit -m "build(frontend): dev proxy to backend on :8080 for /api and /ws"
```

---

### Task A4: Install NgRx + @stomp/stompjs + sockjs-client

**Files:**
- Modify: `frontend/package.json`, `frontend/package-lock.json`

- [ ] **Step 1: Install runtime packages**

```bash
cd frontend
npm install @ngrx/store@^18 @ngrx/effects@^18 @ngrx/store-devtools@^18 @ngrx/entity@^18
npm install @stomp/stompjs sockjs-client
npm install --save-dev @types/sockjs-client
```

- [ ] **Step 2: Verify build**

```bash
npx ng build --configuration=development
```

Expected: SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add frontend/package.json frontend/package-lock.json
git commit -m "build(frontend): add NgRx 18 + @stomp/stompjs + sockjs-client"
```

---

## Phase B — Models + core utilities

### Task B1: TypeScript models (auth + lobby + game + action)

**Files:**
- Create: `frontend/src/app/core/models/auth.model.ts`
- Create: `frontend/src/app/core/models/lobby.model.ts`
- Create: `frontend/src/app/core/models/game.model.ts`
- Create: `frontend/src/app/core/models/action.model.ts`
- Create: `frontend/src/app/core/models/error.model.ts`
- Create: `frontend/src/app/core/models/index.ts` (barrel)

- [ ] **Step 1: Write `auth.model.ts`**

```typescript
export interface User {
  id: string;
  email: string;
  username: string;
  emailVerified: boolean;
  createdAt: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  accessExpiresAt: string;
}
```

- [ ] **Step 2: Write `lobby.model.ts`**

```typescript
export type GameVisibility = 'PRIVATE' | 'PUBLIC';
export type Mode = 'ETALAT' | 'TABLA';
export type Difficulty = 'EASY' | 'MED' | 'HARD';

export interface LobbyGame {
  id: string;
  ownerId: string;
  visibility: GameVisibility;
  joinCode: string | null;
  numPlayers: number;
  mode: Mode;
  difficulty: Difficulty;
  seatsTaken: number;
  started: boolean;
  createdAt: string;
}
```

- [ ] **Step 3: Write `game.model.ts`**

```typescript
import { Mode, Difficulty } from './lobby.model';

export type PieceColor = 'RED' | 'YELLOW' | 'BLUE' | 'BLACK' | 'JOKER';
export type Phase = 'DRAW' | 'ACTION' | 'DISCARD';
export type DrawSource = 'STOCK' | 'DISCARD';

export interface Piece {
  id: number;
  num: number;
  color: PieceColor;
  isJoker: boolean;
}

export interface PlayerView {
  name: string;
  isBot: boolean;
  hasEtalat: boolean;
  calledAtu: boolean;
  announced: boolean;
  mustUsePieceId: number | null;
  hand: Piece[];
  handCount: number;
}

export interface Meld {
  owner: number;
  type: 'GROUP' | 'SUITE';
  pieces: Piece[];
  placedBy: Record<number, number>;
}

export interface GameView {
  id: string;
  players: PlayerView[];
  stockCount: number;
  discard: Piece[];
  atu: Piece;
  melds: Meld[];
  current: number;
  phase: Phase;
  drewFrom: DrawSource | null;
  turnTaken: number;
  round: number;
  mode: Mode;
  difficulty: Difficulty;
  doubleGame: boolean;
  closed: boolean;
  totals: number[];
}

export interface DomainEvent {
  type: string;
  [key: string]: unknown;
}
```

- [ ] **Step 4: Write `action.model.ts`**

```typescript
export interface MeldProposal {
  type: 'GROUP' | 'SUITE';
  pieceIds: number[];
}

export interface LayoffProposal {
  pieceId: number;
  meldIdx: number;
}

export type Action =
  | { type: 'DRAW_FROM_STOCK'; playerIdx: number }
  | { type: 'TAKE_DISCARD'; playerIdx: number; discardIdx: number }
  | { type: 'ETALAT'; playerIdx: number; melds: MeldProposal[] }
  | { type: 'LAYOFF'; playerIdx: number; layoffs: LayoffProposal[] }
  | { type: 'DISCARD'; playerIdx: number; pieceId: number }
  | { type: 'FORCE_AUTO'; playerIdx: number };

export type ActionType = Action['type'];
```

- [ ] **Step 5: Write `error.model.ts`**

```typescript
export interface ApiError {
  code: string;
  message: string;
}
```

- [ ] **Step 6: Write barrel `index.ts`**

```typescript
export * from './auth.model';
export * from './lobby.model';
export * from './game.model';
export * from './action.model';
export * from './error.model';
```

- [ ] **Step 7: Build + commit**

```bash
cd frontend && npx ng build --configuration=development
git add frontend/src/app/core/models/
git commit -m "feat(frontend): add core model types (auth/lobby/game/action/error)"
```

---

### Task B2: `AuthStorageService` + TDD

**Files:**
- Create: `frontend/src/app/core/auth/auth-storage.service.ts`
- Create: `frontend/src/app/core/auth/auth-storage.service.spec.ts`

- [ ] **Step 1: Write failing test**

```typescript
import { TestBed } from '@angular/core/testing';
import { AuthStorageService } from './auth-storage.service';
import { AuthTokens } from '../models';

describe('AuthStorageService', () => {
  let service: AuthStorageService;
  const TOKENS: AuthTokens = {
    accessToken: 'access-1',
    refreshToken: 'refresh-1',
    accessExpiresAt: '2026-12-31T00:00:00Z',
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(AuthStorageService);
  });

  it('returns null when nothing stored', () => {
    expect(service.getTokens()).toBeNull();
  });

  it('stores and reads back tokens', () => {
    service.setTokens(TOKENS);
    expect(service.getTokens()).toEqual(TOKENS);
  });

  it('clear removes stored tokens', () => {
    service.setTokens(TOKENS);
    service.clear();
    expect(service.getTokens()).toBeNull();
  });

  it('returns null when stored JSON is malformed', () => {
    localStorage.setItem('remi.auth.tokens', '{not-json');
    expect(service.getTokens()).toBeNull();
  });
});
```

- [ ] **Step 2: Run (FAIL — class missing)**

```bash
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --include='**/auth-storage.service.spec.ts'
```

Expected: compilation failure.

- [ ] **Step 3: Write `auth-storage.service.ts`**

```typescript
import { Injectable } from '@angular/core';
import { AuthTokens } from '../models';

const STORAGE_KEY = 'remi.auth.tokens';

@Injectable({ providedIn: 'root' })
export class AuthStorageService {
  setTokens(tokens: AuthTokens): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens));
  }

  getTokens(): AuthTokens | null {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw === null) return null;
    try {
      return JSON.parse(raw) as AuthTokens;
    } catch {
      return null;
    }
  }

  clear(): void {
    localStorage.removeItem(STORAGE_KEY);
  }
}
```

- [ ] **Step 4: Run (PASS — 4 tests)** + commit

```bash
npx ng test --watch=false --browsers=ChromeHeadless --include='**/auth-storage.service.spec.ts'
git add frontend/src/app/core/auth/auth-storage.service.ts \
        frontend/src/app/core/auth/auth-storage.service.spec.ts
git commit -m "feat(frontend): AuthStorageService (localStorage backend) + tests"
```

---

### Task B3: Error localization (`ERROR_MESSAGES` + `ErrorMessagePipe`)

**Files:**
- Create: `frontend/src/app/core/i18n/errors.ts`
- Create: `frontend/src/app/core/i18n/error-message.pipe.ts`
- Create: `frontend/src/app/core/i18n/error-message.pipe.spec.ts`

- [ ] **Step 1: Write `errors.ts`**

```typescript
import { ApiError } from '../models';

export const ERROR_MESSAGES: Record<string, string> = {
  // Auth (Stage 2)
  EMAIL_TAKEN: 'Acest email este deja folosit.',
  USERNAME_TAKEN: 'Acest username este deja folosit.',
  INVALID_CREDENTIALS: 'Email sau parolă incorecte.',
  INVALID_TOKEN: 'Link invalid sau expirat.',
  TOKEN_REUSED: 'Sesiunea a fost compromisă. Te-am delogat din toate device-urile.',
  TOKEN_EXPIRED: 'Sesiunea a expirat. Te rugăm să te reloghezi.',
  PASSWORD_POLICY: 'Parola trebuie să aibă minim 10 caractere.',
  USERNAME_POLICY: 'Username 3-20 caractere, litere/cifre/_/-.',
  USER_NOT_FOUND: 'Utilizator inexistent.',
  // Lobby (Stage 3)
  LOBBY_FULL: 'Lobby plin.',
  LOBBY_NOT_FOUND: 'Lobby inexistent.',
  ALREADY_SEATED: 'Ești deja la această masă.',
  NOT_SEATED: 'Nu ești la această masă.',
  NOT_YOUR_SEAT: 'Nu e locul tău.',
  GAME_ALREADY_STARTED: 'Jocul a început deja.',
  JOIN_CODE_NOT_FOUND: 'Cod invalid.',
  ALREADY_QUEUED: 'Ești deja în coada de matchmaking.',
  // Engine (Stage 1)
  NOT_YOUR_TURN: 'Nu e rândul tău.',
  WRONG_PHASE: 'Nu poți face asta acum.',
  GAME_CLOSED: 'Runda este închisă.',
  STOCK_EMPTY: 'Grămada este goală.',
  DISCARD_EMPTY: 'Nu este nicio piesă aruncată.',
  CANNOT_TAKE_OPENING_PIECE: 'Nu poți lua piesa de start.',
  CANNOT_BREAK_LINE: 'Nu poți rupe șirul acum.',
  BREAK_REQUIRES_ETALAT: 'Trebuie să fii etalat pentru a rupe șirul.',
  PIECE_NOT_IN_HAND: 'Piesă inexistentă în mână.',
  INVALID_MELD: 'Combinație invalidă.',
  FIRST_MELD_TOO_FEW_POINTS: 'Prima etalare e sub 45 puncte.',
  FIRST_MELD_NEEDS_SUITE_OR_1S: 'Prima etalare necesită o suită sau o terță de 1.',
  MUST_USE_TAKEN_PIECE: 'Trebuie să folosești piesa luată din șir.',
  NOT_ETALAT: 'Trebuie să fii etalat mai întâi.',
  INVALID_LAYOFF: 'Piesa nu se potrivește.',
  HAND_TOO_FULL_TO_DISCARD: 'Prea multe piese în mână.',
  // System
  GAME_VERSION_CONFLICT: 'Joc actualizat între timp. Refresh.',
  ENGINE_ERROR: 'Eroare internă engine.',
  INTERNAL_ERROR: 'Eroare neașteptată.',
  INVALID_REQUEST: 'Cerere invalidă.',
  // Frontend-only
  NETWORK: 'Eroare de rețea. Verifică conexiunea.',
  UNAUTHORIZED: 'Autentificare necesară.',
  WS_DISCONNECTED: 'Conexiunea s-a întrerupt. Se reconectează...',
  UNKNOWN: 'A apărut o eroare neașteptată.',
};

export function localizeError(error: ApiError | null | undefined): string {
  if (error === null || error === undefined) return '';
  return ERROR_MESSAGES[error.code] ?? error.message ?? ERROR_MESSAGES['UNKNOWN'];
}
```

- [ ] **Step 2: Write failing pipe test `error-message.pipe.spec.ts`**

```typescript
import { ErrorMessagePipe } from './error-message.pipe';

describe('ErrorMessagePipe', () => {
  const pipe = new ErrorMessagePipe();

  it('returns empty string for null', () => {
    expect(pipe.transform(null)).toBe('');
  });

  it('localizes known code', () => {
    expect(pipe.transform({ code: 'EMAIL_TAKEN', message: 'x' }))
        .toBe('Acest email este deja folosit.');
  });

  it('falls back to message for unknown code', () => {
    expect(pipe.transform({ code: 'WEIRD_CODE_123', message: 'fallback message' }))
        .toBe('fallback message');
  });

  it('falls back to UNKNOWN if neither known code nor message', () => {
    expect(pipe.transform({ code: 'WEIRD_CODE_123', message: '' }))
        .toBe('A apărut o eroare neașteptată.');
  });
});
```

- [ ] **Step 3: Run (FAIL — pipe missing)**

- [ ] **Step 4: Write `error-message.pipe.ts`**

```typescript
import { Pipe, PipeTransform } from '@angular/core';
import { ApiError } from '../models';
import { localizeError } from './errors';

@Pipe({ name: 'errorMessage', standalone: true })
export class ErrorMessagePipe implements PipeTransform {
  transform(error: ApiError | null | undefined): string {
    return localizeError(error);
  }
}
```

- [ ] **Step 5: Run (PASS — 4 tests)** + commit

```bash
npx ng test --watch=false --browsers=ChromeHeadless --include='**/error-message.pipe.spec.ts'
git add frontend/src/app/core/i18n/
git commit -m "feat(frontend): error localization (ERROR_MESSAGES + ErrorMessagePipe) + tests"
```

---

### Task B4: `ErrorBannerComponent` (shared)

**Files:**
- Create: `frontend/src/app/shared/error-banner/error-banner.component.ts`
- Create: `frontend/src/app/shared/error-banner/error-banner.component.html`
- Create: `frontend/src/app/shared/error-banner/error-banner.component.scss`
- Create: `frontend/src/app/shared/error-banner/error-banner.component.spec.ts`

- [ ] **Step 1: Write component**

`error-banner.component.ts`:

```typescript
import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ErrorMessagePipe } from '../../core/i18n/error-message.pipe';
import { ApiError } from '../../core/models';

@Component({
  selector: 'app-error-banner',
  standalone: true,
  imports: [CommonModule, ErrorMessagePipe],
  templateUrl: './error-banner.component.html',
  styleUrls: ['./error-banner.component.scss'],
})
export class ErrorBannerComponent {
  @Input() error: ApiError | null = null;
}
```

`error-banner.component.html`:

```html
<div class="error-banner" *ngIf="error" role="alert">
  {{ error | errorMessage }}
</div>
```

`error-banner.component.scss`:

```scss
.error-banner {
  background: var(--ion-color-danger, #eb445a);
  color: white;
  padding: 12px 16px;
  border-radius: 8px;
  margin: 8px 0;
  font-size: 14px;
}
```

- [ ] **Step 2: Write test**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ErrorBannerComponent } from './error-banner.component';

describe('ErrorBannerComponent', () => {
  let fixture: ComponentFixture<ErrorBannerComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ErrorBannerComponent] });
    fixture = TestBed.createComponent(ErrorBannerComponent);
  });

  it('renders nothing when error is null', () => {
    fixture.componentRef.setInput('error', null);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.error-banner')).toBeNull();
  });

  it('renders localized message when error provided', () => {
    fixture.componentRef.setInput('error', { code: 'EMAIL_TAKEN', message: 'raw' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent.trim())
        .toBe('Acest email este deja folosit.');
  });
});
```

- [ ] **Step 3: Run + commit**

```bash
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --include='**/error-banner.component.spec.ts'
git add frontend/src/app/shared/error-banner/
git commit -m "feat(frontend): shared ErrorBannerComponent with localized messages"
```

---

### Task B5: `WsConnectionState` type + (placeholder) `WsIndicatorComponent`

**Files:**
- Create: `frontend/src/app/core/ws/ws-state.ts`
- Create: `frontend/src/app/shared/ws-indicator/ws-indicator.component.ts`
- Create: `frontend/src/app/shared/ws-indicator/ws-indicator.component.html`
- Create: `frontend/src/app/shared/ws-indicator/ws-indicator.component.scss`

- [ ] **Step 1: Write `ws-state.ts`**

```typescript
export type WsConnectionState = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'RECONNECTING';
```

- [ ] **Step 2: Write `ws-indicator.component.ts`**

```typescript
import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { WsConnectionState } from '../../core/ws/ws-state';

@Component({
  selector: 'app-ws-indicator',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ws-indicator.component.html',
  styleUrls: ['./ws-indicator.component.scss'],
})
export class WsIndicatorComponent {
  @Input() state: WsConnectionState = 'DISCONNECTED';
}
```

`ws-indicator.component.html`:

```html
<span class="ws-indicator" [attr.data-state]="state" [title]="state">
  <ng-container [ngSwitch]="state">
    <span *ngSwitchCase="'CONNECTED'" class="dot connected"></span>
    <span *ngSwitchCase="'CONNECTING'" class="dot spinning">⟳</span>
    <span *ngSwitchCase="'RECONNECTING'" class="dot spinning warn">⟳</span>
    <span *ngSwitchCase="'DISCONNECTED'" class="dot disconnected"></span>
  </ng-container>
</span>
```

`ws-indicator.component.scss`:

```scss
.ws-indicator {
  display: inline-flex;
  align-items: center;
  margin-left: 8px;
  .dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }
  .dot.connected { background: #2dd36f; }
  .dot.disconnected { background: #eb445a; }
  .dot.spinning { background: transparent; color: #ffc409; animation: spin 1s linear infinite;
                  font-size: 12px; line-height: 1; }
  .dot.spinning.warn { color: #ffc409; }
}
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
```

(No test — purely presentational, rendered in AppComponent test later.)

- [ ] **Step 3: Build + commit**

```bash
cd frontend && npx ng build --configuration=development
git add frontend/src/app/core/ws/ws-state.ts frontend/src/app/shared/ws-indicator/
git commit -m "feat(frontend): WsConnectionState type + WsIndicatorComponent (presentational)"
```

---

### Task B6: `GlobalErrorHandler`

**Files:**
- Create: `frontend/src/app/shared/global-error-handler.ts`
- Create: `frontend/src/app/shared/global-error-handler.spec.ts`

- [ ] **Step 1: Write test**

```typescript
import { TestBed } from '@angular/core/testing';
import { ToastController } from '@ionic/angular/standalone';
import { GlobalErrorHandler } from './global-error-handler';

describe('GlobalErrorHandler', () => {
  let handler: GlobalErrorHandler;
  let toastSpy: jasmine.SpyObj<ToastController>;

  beforeEach(() => {
    toastSpy = jasmine.createSpyObj('ToastController', ['create']);
    toastSpy.create.and.returnValue(Promise.resolve({ present: () => Promise.resolve() } as any));
    TestBed.configureTestingModule({
      providers: [
        GlobalErrorHandler,
        { provide: ToastController, useValue: toastSpy },
      ],
    });
    handler = TestBed.inject(GlobalErrorHandler);
  });

  it('logs to console and shows toast on unhandled error', async () => {
    spyOn(console, 'error');
    handler.handleError(new Error('boom'));
    expect(console.error).toHaveBeenCalled();
    expect(toastSpy.create).toHaveBeenCalledWith(jasmine.objectContaining({
      message: 'A apărut o eroare neașteptată.',
      duration: 4000,
      color: 'danger',
    }));
  });
});
```

- [ ] **Step 2: Run (FAIL)**

- [ ] **Step 3: Write `global-error-handler.ts`**

```typescript
import { ErrorHandler, Injectable, inject } from '@angular/core';
import { ToastController } from '@ionic/angular/standalone';

@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  private readonly toasts = inject(ToastController);

  handleError(error: unknown): void {
    console.error('[GlobalErrorHandler]', error);
    this.toasts.create({
      message: 'A apărut o eroare neașteptată.',
      duration: 4000,
      color: 'danger',
    }).then(t => t.present()).catch(() => { /* swallow toast errors */ });
  }
}
```

- [ ] **Step 4: Run (PASS) + commit**

```bash
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --include='**/global-error-handler.spec.ts'
git add frontend/src/app/shared/global-error-handler.ts \
        frontend/src/app/shared/global-error-handler.spec.ts
git commit -m "feat(frontend): GlobalErrorHandler with toast + console log"
```

---

## End of Part 1

Continuă cu Part 2 (`2026-05-17-stage4a-frontend-part2.md`) — Phases C (API services), D (WebSocket services), E (NgRx stores).
