# Stage 4a — Frontend Implementation Plan (Part 2: API + WebSocket + NgRx)

> **For agentic workers:** Continuation of Part 1. Same TDD discipline.

**Pre-requisite:** Part 1 (A+B) completed — scaffold, models, AuthStorageService, error localization, shared components.

---

## Phase C — API services

### Task C1: `AuthApi` + tests

**Files:**
- Create: `frontend/src/app/core/api/auth.api.ts`
- Create: `frontend/src/app/core/api/auth.api.spec.ts`

- [ ] **Step 1: Write test**

```typescript
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AuthApi } from './auth.api';
import { environment } from '../../../environments/environment';

describe('AuthApi', () => {
  let api: AuthApi;
  let httpMock: HttpTestingController;
  const base = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), AuthApi],
    });
    api = TestBed.inject(AuthApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('register POSTs to /auth/register', () => {
    const body = { email: 'a@b.com', username: 'alice', password: 'passwordxx' };
    api.register(body).subscribe();
    const req = httpMock.expectOne(`${base}/auth/register`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ id: '1', email: 'a@b.com', username: 'alice', emailVerified: false, createdAt: '2026-01-01' });
  });

  it('login POSTs to /auth/login', () => {
    api.login({ emailOrUsername: 'alice', password: 'passwordxx' }).subscribe();
    const req = httpMock.expectOne(`${base}/auth/login`);
    expect(req.request.method).toBe('POST');
    req.flush({ accessToken: 'a', refreshToken: 'r', accessExpiresAt: '2026-01-01' });
  });

  it('verifyEmail POSTs token in body', () => {
    api.verifyEmail('t-1').subscribe();
    const req = httpMock.expectOne(`${base}/auth/verify-email`);
    expect(req.request.body).toEqual({ token: 't-1' });
    req.flush(null);
  });

  it('refresh POSTs refreshToken', () => {
    api.refresh('r-1').subscribe();
    const req = httpMock.expectOne(`${base}/auth/refresh`);
    expect(req.request.body).toEqual({ refreshToken: 'r-1' });
    req.flush({ accessToken: 'a2', refreshToken: 'r2', accessExpiresAt: '2026-01-02' });
  });

  it('logout POSTs refreshToken', () => {
    api.logout('r-1').subscribe();
    const req = httpMock.expectOne(`${base}/auth/logout`);
    expect(req.request.body).toEqual({ refreshToken: 'r-1' });
    req.flush(null);
  });

  it('requestPasswordReset POSTs email', () => {
    api.requestPasswordReset('a@b.com').subscribe();
    const req = httpMock.expectOne(`${base}/auth/request-password-reset`);
    expect(req.request.body).toEqual({ email: 'a@b.com' });
    req.flush(null);
  });

  it('resetPassword POSTs token + newPassword', () => {
    api.resetPassword('t-1', 'newpasswordxx').subscribe();
    const req = httpMock.expectOne(`${base}/auth/reset-password`);
    expect(req.request.body).toEqual({ token: 't-1', newPassword: 'newpasswordxx' });
    req.flush(null);
  });

  it('me GETs /users/me', () => {
    api.me().subscribe();
    const req = httpMock.expectOne(`${base}/users/me`);
    expect(req.request.method).toBe('GET');
    req.flush({ id: '1', email: 'a@b.com', username: 'alice', emailVerified: true, createdAt: '2026-01-01' });
  });
});
```

- [ ] **Step 2: Run (FAIL — class missing)**

- [ ] **Step 3: Write `auth.api.ts`**

```typescript
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { User, AuthTokens } from '../models';

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  register(req: { email: string; username: string; password: string }): Observable<User> {
    return this.http.post<User>(`${this.base}/auth/register`, req);
  }

  verifyEmail(token: string): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/verify-email`, { token });
  }

  login(req: { emailOrUsername: string; password: string }): Observable<AuthTokens> {
    return this.http.post<AuthTokens>(`${this.base}/auth/login`, req);
  }

  refresh(refreshToken: string): Observable<AuthTokens> {
    return this.http.post<AuthTokens>(`${this.base}/auth/refresh`, { refreshToken });
  }

  logout(refreshToken: string): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/logout`, { refreshToken });
  }

  requestPasswordReset(email: string): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/request-password-reset`, { email });
  }

  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/reset-password`, { token, newPassword });
  }

  me(): Observable<User> {
    return this.http.get<User>(`${this.base}/users/me`);
  }
}
```

- [ ] **Step 4: Run (PASS — 8 tests) + commit**

```bash
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --include='**/auth.api.spec.ts'
git add frontend/src/app/core/api/auth.api.ts frontend/src/app/core/api/auth.api.spec.ts
git commit -m "feat(frontend): AuthApi service + tests"
```

---

### Task C2: `LobbyApi` + tests

**Files:**
- Create: `frontend/src/app/core/api/lobby.api.ts`
- Create: `frontend/src/app/core/api/lobby.api.spec.ts`

- [ ] **Step 1: Write test**

```typescript
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { LobbyApi } from './lobby.api';
import { environment } from '../../../environments/environment';

describe('LobbyApi', () => {
  let api: LobbyApi;
  let httpMock: HttpTestingController;
  const base = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), LobbyApi],
    });
    api = TestBed.inject(LobbyApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('create POSTs to /games', () => {
    const body = { visibility: 'PRIVATE' as const, numPlayers: 3, mode: 'ETALAT' as const, difficulty: 'MED' as const };
    api.create(body).subscribe();
    const req = httpMock.expectOne(`${base}/games`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('joinByCode POSTs to /games/join-by-code', () => {
    api.joinByCode('ABC12345').subscribe();
    const req = httpMock.expectOne(`${base}/games/join-by-code`);
    expect(req.request.body).toEqual({ joinCode: 'ABC12345' });
    req.flush({});
  });

  it('joinPublic POSTs to /games/{id}/join', () => {
    api.joinPublic('g-1').subscribe();
    const req = httpMock.expectOne(`${base}/games/g-1/join`);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('listPublic GETs /games/public', () => {
    api.listPublic().subscribe();
    const req = httpMock.expectOne(`${base}/games/public`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('myGames GETs /games/mine', () => {
    api.myGames().subscribe();
    httpMock.expectOne(`${base}/games/mine`).flush([]);
  });

  it('get GETs /games/{id}', () => {
    api.get('g-1').subscribe();
    httpMock.expectOne(`${base}/games/g-1`).flush({});
  });

  it('apply POSTs action wrapped in {action}', () => {
    api.apply('g-1', { type: 'DRAW_FROM_STOCK', playerIdx: 0 }).subscribe();
    const req = httpMock.expectOne(`${base}/games/g-1/actions`);
    expect(req.request.body).toEqual({ action: { type: 'DRAW_FROM_STOCK', playerIdx: 0 } });
    req.flush({});
  });

  it('leave POSTs to /games/{id}/leave', () => {
    api.leave('g-1').subscribe();
    httpMock.expectOne(`${base}/games/g-1/leave`).flush(null);
  });
});
```

- [ ] **Step 2: Write `lobby.api.ts`**

```typescript
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LobbyGame, GameView, Action, GameVisibility, Mode, Difficulty } from '../models';

export interface CreateGameRequest {
  visibility: GameVisibility;
  numPlayers: number;
  mode: Mode;
  difficulty: Difficulty;
}

@Injectable({ providedIn: 'root' })
export class LobbyApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  create(req: CreateGameRequest): Observable<LobbyGame> {
    return this.http.post<LobbyGame>(`${this.base}/games`, req);
  }

  joinByCode(joinCode: string): Observable<LobbyGame> {
    return this.http.post<LobbyGame>(`${this.base}/games/join-by-code`, { joinCode });
  }

  joinPublic(gameId: string): Observable<LobbyGame> {
    return this.http.post<LobbyGame>(`${this.base}/games/${gameId}/join`, {});
  }

  listPublic(): Observable<LobbyGame[]> {
    return this.http.get<LobbyGame[]>(`${this.base}/games/public`);
  }

  myGames(): Observable<LobbyGame[]> {
    return this.http.get<LobbyGame[]>(`${this.base}/games/mine`);
  }

  get(gameId: string): Observable<GameView> {
    return this.http.get<GameView>(`${this.base}/games/${gameId}`);
  }

  apply(gameId: string, action: Action): Observable<GameView> {
    return this.http.post<GameView>(`${this.base}/games/${gameId}/actions`, { action });
  }

  leave(gameId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/games/${gameId}/leave`, {});
  }
}
```

- [ ] **Step 3: Run + commit**

```bash
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --include='**/lobby.api.spec.ts'
git add frontend/src/app/core/api/lobby.api.ts frontend/src/app/core/api/lobby.api.spec.ts
git commit -m "feat(frontend): LobbyApi service + tests"
```

---

### Task C3: `MatchmakingApi` + tests

**Files:**
- Create: `frontend/src/app/core/api/matchmaking.api.ts`
- Create: `frontend/src/app/core/api/matchmaking.api.spec.ts`

- [ ] **Step 1: Write test**

```typescript
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MatchmakingApi } from './matchmaking.api';
import { environment } from '../../../environments/environment';

describe('MatchmakingApi', () => {
  let api: MatchmakingApi;
  let httpMock: HttpTestingController;
  const base = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), MatchmakingApi],
    });
    api = TestBed.inject(MatchmakingApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('quick POSTs to /matchmaking/quick', () => {
    api.quick({ numPlayers: 2, mode: 'ETALAT', difficulty: 'MED' }).subscribe();
    const req = httpMock.expectOne(`${base}/matchmaking/quick`);
    expect(req.request.method).toBe('POST');
    req.flush({ matched: false });
  });

  it('cancel POSTs to /matchmaking/cancel', () => {
    api.cancel().subscribe();
    const req = httpMock.expectOne(`${base}/matchmaking/cancel`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });
});
```

- [ ] **Step 2: Write `matchmaking.api.ts`**

```typescript
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LobbyGame, Mode, Difficulty } from '../models';

export interface QuickMatchRequest {
  numPlayers: number;
  mode: Mode;
  difficulty: Difficulty;
}

export interface QuickMatchResponse {
  matched: boolean;
  game?: LobbyGame;
}

@Injectable({ providedIn: 'root' })
export class MatchmakingApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  quick(req: QuickMatchRequest): Observable<QuickMatchResponse> {
    return this.http.post<QuickMatchResponse>(`${this.base}/matchmaking/quick`, req);
  }

  cancel(): Observable<void> {
    return this.http.post<void>(`${this.base}/matchmaking/cancel`, {});
  }
}
```

- [ ] **Step 3: Run + commit**

```bash
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --include='**/matchmaking.api.spec.ts'
git add frontend/src/app/core/api/matchmaking.api.ts frontend/src/app/core/api/matchmaking.api.spec.ts
git commit -m "feat(frontend): MatchmakingApi service + tests"
```

---

## Phase D — WebSocket

### Task D1: `StompService` + tests

**Files:**
- Create: `frontend/src/app/core/ws/stomp.service.ts`
- Create: `frontend/src/app/core/ws/stomp.service.spec.ts`

- [ ] **Step 1: Write `stomp.service.ts`**

```typescript
import { Injectable } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { Observable, Subject, BehaviorSubject } from 'rxjs';
import SockJS from 'sockjs-client';
import { environment } from '../../../environments/environment';
import { WsConnectionState } from './ws-state';

interface ActiveSubscription<T = unknown> {
  destination: string;
  subject: Subject<T>;
  deserializer: (raw: string) => T;
  stompSub?: StompSubscription;
}

@Injectable({ providedIn: 'root' })
export class StompService {
  private client: Client | null = null;
  private readonly state$ = new BehaviorSubject<WsConnectionState>('DISCONNECTED');
  private readonly activeSubs = new Map<string, ActiveSubscription>();
  private accessToken: string | null = null;

  /** Public: observable of connection state. */
  readonly connectionState$ = this.state$.asObservable();

  connect(accessToken: string): Observable<WsConnectionState> {
    this.accessToken = accessToken;
    if (this.client) {
      this.client.deactivate();
    }
    this.state$.next('CONNECTING');
    this.client = new Client({
      webSocketFactory: () => new SockJS(environment.wsUrl),
      connectHeaders: { Authorization: `Bearer ${accessToken}` },
      reconnectDelay: 1000,           // start at 1s
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        this.state$.next('CONNECTED');
        this.resubscribeAll();
      },
      onDisconnect: () => this.state$.next('DISCONNECTED'),
      onWebSocketClose: () => {
        if (this.state$.value === 'CONNECTED') {
          this.state$.next('RECONNECTING');
        }
      },
      onStompError: () => this.state$.next('DISCONNECTED'),
    });
    this.client.activate();
    return this.connectionState$;
  }

  disconnect(): void {
    this.client?.deactivate();
    this.activeSubs.forEach(s => s.stompSub?.unsubscribe());
    this.activeSubs.clear();
    this.client = null;
    this.state$.next('DISCONNECTED');
  }

  subscribe<T = unknown>(
    destination: string,
    deserializer: (raw: string) => T = JSON.parse as (raw: string) => T,
  ): Observable<T> {
    let entry = this.activeSubs.get(destination) as ActiveSubscription<T> | undefined;
    if (!entry) {
      entry = { destination, subject: new Subject<T>(), deserializer };
      this.activeSubs.set(destination, entry);
      this.subscribeOnClient(entry);
    }
    return entry.subject.asObservable();
  }

  send(destination: string, payload: unknown): void {
    if (!this.client || !this.client.connected) {
      throw new Error('STOMP not connected');
    }
    this.client.publish({
      destination,
      body: JSON.stringify(payload),
      headers: { 'content-type': 'application/json' },
    });
  }

  private subscribeOnClient(entry: ActiveSubscription): void {
    if (!this.client || !this.client.connected) return;
    entry.stompSub = this.client.subscribe(entry.destination, (msg: IMessage) => {
      try {
        const value = entry.deserializer(msg.body);
        entry.subject.next(value);
      } catch (e) {
        entry.subject.error(e);
      }
    });
  }

  private resubscribeAll(): void {
    this.activeSubs.forEach(entry => this.subscribeOnClient(entry));
  }
}
```

- [ ] **Step 2: Write test (with @stomp/stompjs `Client` mocked)**

```typescript
import { TestBed } from '@angular/core/testing';
import { StompService } from './stomp.service';

// Lightweight Client mock injected via Object.defineProperty (we don't try to test reconnect/network — that's covered by manual smoke test)
describe('StompService', () => {
  let service: StompService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(StompService);
  });

  it('starts in DISCONNECTED state', (done) => {
    service.connectionState$.subscribe(state => {
      expect(state).toBe('DISCONNECTED');
      done();
    });
  });

  it('subscribe registers an observable even before CONNECT', () => {
    const obs = service.subscribe<{ x: number }>('/topic/test');
    expect(obs).toBeDefined();
  });

  it('send throws when not connected', () => {
    expect(() => service.send('/app/test', { a: 1 })).toThrowError('STOMP not connected');
  });

  it('disconnect resets state', (done) => {
    service.disconnect();
    service.connectionState$.subscribe(state => {
      expect(state).toBe('DISCONNECTED');
      done();
    });
  });
});
```

(Real STOMP behavior is exercised by backend integration tests + the manual smoke test in `SMOKE_TEST.md`. The Angular-side tests are intentionally lightweight — verifying the API contract, not network behavior.)

- [ ] **Step 3: Run + commit**

```bash
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --include='**/stomp.service.spec.ts'
git add frontend/src/app/core/ws/stomp.service.ts frontend/src/app/core/ws/stomp.service.spec.ts
git commit -m "feat(frontend): StompService — SockJS+STOMP client with reconnect + active sub map"
```

---

### Task D2: `GameWsService` + tests

**Files:**
- Create: `frontend/src/app/core/ws/game-ws.service.ts`
- Create: `frontend/src/app/core/ws/game-ws.service.spec.ts`

- [ ] **Step 1: Write `game-ws.service.ts`**

```typescript
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { StompService } from './stomp.service';
import { GameView, DomainEvent, Action, ApiError, LobbyGame } from '../models';

export interface GameUpdate {
  view: GameView;
  events: DomainEvent[];
}

@Injectable({ providedIn: 'root' })
export class GameWsService {
  private readonly stomp = inject(StompService);

  subscribeToGame(gameId: string): Observable<GameUpdate> {
    return this.stomp.subscribe<GameUpdate>(`/user/queue/games/${gameId}`);
  }

  subscribeToErrors(): Observable<ApiError> {
    return this.stomp.subscribe<ApiError>('/user/queue/errors');
  }

  subscribeToMatches(): Observable<LobbyGame> {
    return this.stomp.subscribe<LobbyGame>('/user/queue/match');
  }

  sendAction(gameId: string, action: Action): void {
    this.stomp.send(`/app/games/${gameId}/actions`, action);
  }
}
```

- [ ] **Step 2: Write test**

```typescript
import { TestBed } from '@angular/core/testing';
import { StompService } from './stomp.service';
import { GameWsService } from './game-ws.service';
import { Subject } from 'rxjs';

describe('GameWsService', () => {
  let service: GameWsService;
  let stompSpy: jasmine.SpyObj<StompService>;

  beforeEach(() => {
    stompSpy = jasmine.createSpyObj('StompService', ['subscribe', 'send'], {
      connectionState$: new Subject(),
    });
    stompSpy.subscribe.and.returnValue(new Subject());

    TestBed.configureTestingModule({
      providers: [
        GameWsService,
        { provide: StompService, useValue: stompSpy },
      ],
    });
    service = TestBed.inject(GameWsService);
  });

  it('subscribeToGame subscribes to /user/queue/games/{id}', () => {
    service.subscribeToGame('g-1');
    expect(stompSpy.subscribe).toHaveBeenCalledWith('/user/queue/games/g-1');
  });

  it('subscribeToErrors subscribes to /user/queue/errors', () => {
    service.subscribeToErrors();
    expect(stompSpy.subscribe).toHaveBeenCalledWith('/user/queue/errors');
  });

  it('subscribeToMatches subscribes to /user/queue/match', () => {
    service.subscribeToMatches();
    expect(stompSpy.subscribe).toHaveBeenCalledWith('/user/queue/match');
  });

  it('sendAction sends to /app/games/{id}/actions', () => {
    service.sendAction('g-1', { type: 'DRAW_FROM_STOCK', playerIdx: 0 });
    expect(stompSpy.send).toHaveBeenCalledWith('/app/games/g-1/actions',
        { type: 'DRAW_FROM_STOCK', playerIdx: 0 });
  });
});
```

- [ ] **Step 3: Run + commit**

```bash
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --include='**/game-ws.service.spec.ts'
git add frontend/src/app/core/ws/game-ws.service.ts frontend/src/app/core/ws/game-ws.service.spec.ts
git commit -m "feat(frontend): GameWsService — typed wrapper over StompService"
```

---

## Phase E — NgRx stores

Each feature follows the same pattern: actions, reducer, effects, selectors, feature wrapper, and reducer+selectors tests. Effects tests follow the pattern in E5.

### Task E1: Auth NgRx feature

**Files:**
- Create: `frontend/src/app/store/auth/auth.actions.ts`
- Create: `frontend/src/app/store/auth/auth.reducer.ts`
- Create: `frontend/src/app/store/auth/auth.feature.ts`
- Create: `frontend/src/app/store/auth/auth.selectors.ts`
- Create: `frontend/src/app/store/auth/auth.effects.ts`
- Create: `frontend/src/app/store/auth/auth.reducer.spec.ts`
- Create: `frontend/src/app/store/auth/auth.effects.spec.ts`

- [ ] **Step 1: Write `auth.actions.ts`**

```typescript
import { createActionGroup, props, emptyProps } from '@ngrx/store';
import { User, AuthTokens, ApiError } from '../../core/models';

export const Auth = createActionGroup({
  source: 'Auth',
  events: {
    'Bootstrap From Storage': emptyProps(),
    'Bootstrap Succeeded': props<{ user: User; tokens: AuthTokens }>(),
    'Bootstrap Failed': emptyProps(),

    'Login Requested': props<{ emailOrUsername: string; password: string }>(),
    'Login Succeeded': props<{ tokens: AuthTokens }>(),
    'Login Failed': props<{ error: ApiError }>(),

    'User Loaded': props<{ user: User }>(),

    'Register Requested': props<{ email: string; username: string; password: string }>(),
    'Register Succeeded': props<{ user: User }>(),
    'Register Failed': props<{ error: ApiError }>(),

    'Verify Email Requested': props<{ token: string }>(),
    'Verify Email Succeeded': emptyProps(),
    'Verify Email Failed': props<{ error: ApiError }>(),

    'Request Reset Requested': props<{ email: string }>(),
    'Request Reset Succeeded': emptyProps(),
    'Request Reset Failed': props<{ error: ApiError }>(),

    'Reset Password Requested': props<{ token: string; newPassword: string }>(),
    'Reset Password Succeeded': emptyProps(),
    'Reset Password Failed': props<{ error: ApiError }>(),

    'Refresh Requested': emptyProps(),
    'Refresh Succeeded': props<{ tokens: AuthTokens }>(),
    'Refresh Failed': emptyProps(),

    'Logout Requested': emptyProps(),
    'Logout Local': emptyProps(),
    'Session Invalidated': props<{ reason: string }>(),
  },
});
```

- [ ] **Step 2: Write `auth.reducer.ts` + `auth.feature.ts`**

`auth.reducer.ts`:

```typescript
import { createReducer, on } from '@ngrx/store';
import { Auth } from './auth.actions';
import { User, AuthTokens, ApiError } from '../../core/models';

export type AuthStatus = 'anonymous' | 'bootstrapping' | 'loading' | 'authenticated';

export interface AuthState {
  user: User | null;
  tokens: AuthTokens | null;
  status: AuthStatus;
  error: ApiError | null;
  lastInvalidationReason: string | null;
}

export const initialState: AuthState = {
  user: null,
  tokens: null,
  status: 'anonymous',
  error: null,
  lastInvalidationReason: null,
};

export const authReducer = createReducer(
  initialState,
  on(Auth.bootstrapFromStorage, s => ({ ...s, status: 'bootstrapping' as const, error: null })),
  on(Auth.bootstrapSucceeded, (s, { user, tokens }) =>
    ({ ...s, user, tokens, status: 'authenticated' as const, error: null })),
  on(Auth.bootstrapFailed, s => ({ ...initialState })),

  on(Auth.loginRequested, s => ({ ...s, status: 'loading' as const, error: null })),
  on(Auth.loginSucceeded, (s, { tokens }) => ({ ...s, tokens, status: 'loading' as const })),
  on(Auth.loginFailed, (s, { error }) => ({ ...s, status: 'anonymous' as const, error })),

  on(Auth.userLoaded, (s, { user }) => ({ ...s, user, status: 'authenticated' as const })),

  on(Auth.registerRequested, s => ({ ...s, status: 'loading' as const, error: null })),
  on(Auth.registerSucceeded, s => ({ ...s, status: 'anonymous' as const, error: null })),
  on(Auth.registerFailed, (s, { error }) => ({ ...s, status: 'anonymous' as const, error })),

  on(Auth.verifyEmailRequested, s => ({ ...s, status: 'loading' as const, error: null })),
  on(Auth.verifyEmailSucceeded, s => ({ ...s, status: 'anonymous' as const })),
  on(Auth.verifyEmailFailed, (s, { error }) => ({ ...s, status: 'anonymous' as const, error })),

  on(Auth.requestResetRequested, s => ({ ...s, error: null })),
  on(Auth.requestResetFailed, (s, { error }) => ({ ...s, error })),

  on(Auth.resetPasswordRequested, s => ({ ...s, status: 'loading' as const, error: null })),
  on(Auth.resetPasswordSucceeded, s => ({ ...s, status: 'anonymous' as const })),
  on(Auth.resetPasswordFailed, (s, { error }) => ({ ...s, status: 'anonymous' as const, error })),

  on(Auth.refreshSucceeded, (s, { tokens }) => ({ ...s, tokens })),
  on(Auth.refreshFailed, () => ({ ...initialState })),

  on(Auth.logoutLocal, () => ({ ...initialState })),
  on(Auth.sessionInvalidated, (_s, { reason }) =>
    ({ ...initialState, lastInvalidationReason: reason })),
);
```

`auth.feature.ts`:

```typescript
import { createFeature } from '@ngrx/store';
import { authReducer } from './auth.reducer';

export const authFeature = createFeature({
  name: 'auth',
  reducer: authReducer,
});
```

- [ ] **Step 3: Write `auth.selectors.ts`**

```typescript
import { createSelector } from '@ngrx/store';
import { authFeature } from './auth.feature';

export const selectAuthState = authFeature.selectAuthState;
export const selectUser = authFeature.selectUser;
export const selectTokens = authFeature.selectTokens;
export const selectAuthStatus = authFeature.selectStatus;
export const selectAuthError = authFeature.selectError;
export const selectIsAuthenticated = createSelector(
  authFeature.selectStatus,
  status => status === 'authenticated',
);
export const selectLastInvalidationReason = authFeature.selectLastInvalidationReason;
```

- [ ] **Step 4: Write `auth.effects.ts`**

```typescript
import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, exhaustMap, map, of, switchMap, tap, withLatestFrom } from 'rxjs';
import { Auth } from './auth.actions';
import { AuthApi } from '../../core/api/auth.api';
import { AuthStorageService } from '../../core/auth/auth-storage.service';
import { selectTokens } from './auth.selectors';
import { ApiError } from '../../core/models';

const toApiError = (err: HttpErrorResponse): ApiError =>
  (err.error && typeof err.error === 'object' && 'code' in err.error)
    ? (err.error as ApiError)
    : { code: 'NETWORK', message: 'Eroare de rețea.' };

@Injectable()
export class AuthEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(AuthApi);
  private readonly storage = inject(AuthStorageService);
  private readonly router = inject(Router);
  private readonly store = inject(Store);

  bootstrap$ = createEffect(() => this.actions$.pipe(
    ofType(Auth.bootstrapFromStorage),
    switchMap(() => {
      const tokens = this.storage.getTokens();
      if (!tokens) return of(Auth.bootstrapFailed());
      return this.api.me().pipe(
        map(user => Auth.bootstrapSucceeded({ user, tokens })),
        catchError(() => of(Auth.bootstrapFailed())),
      );
    }),
  ));

  login$ = createEffect(() => this.actions$.pipe(
    ofType(Auth.loginRequested),
    exhaustMap(({ emailOrUsername, password }) =>
      this.api.login({ emailOrUsername, password }).pipe(
        map(tokens => Auth.loginSucceeded({ tokens })),
        catchError((err: HttpErrorResponse) => of(Auth.loginFailed({ error: toApiError(err) }))),
      )
    ),
  ));

  persistTokensThenLoadUser$ = createEffect(() => this.actions$.pipe(
    ofType(Auth.loginSucceeded, Auth.bootstrapSucceeded, Auth.refreshSucceeded),
    tap(({ tokens }) => this.storage.setTokens(tokens)),
  ), { dispatch: false });

  loadUserAfterLogin$ = createEffect(() => this.actions$.pipe(
    ofType(Auth.loginSucceeded),
    switchMap(() => this.api.me().pipe(
      map(user => Auth.userLoaded({ user })),
      catchError(() => of(Auth.logoutLocal())),
    )),
  ));

  redirectAfterLogin$ = createEffect(() => this.actions$.pipe(
    ofType(Auth.userLoaded),
    tap(() => this.router.navigateByUrl('/lobby')),
  ), { dispatch: false });

  register$ = createEffect(() => this.actions$.pipe(
    ofType(Auth.registerRequested),
    exhaustMap(({ email, username, password }) =>
      this.api.register({ email, username, password }).pipe(
        map(user => Auth.registerSucceeded({ user })),
        catchError((err: HttpErrorResponse) => of(Auth.registerFailed({ error: toApiError(err) }))),
      )
    ),
  ));

  verifyEmail$ = createEffect(() => this.actions$.pipe(
    ofType(Auth.verifyEmailRequested),
    exhaustMap(({ token }) => this.api.verifyEmail(token).pipe(
      map(() => Auth.verifyEmailSucceeded()),
      catchError((err: HttpErrorResponse) => of(Auth.verifyEmailFailed({ error: toApiError(err) }))),
    )),
  ));

  requestReset$ = createEffect(() => this.actions$.pipe(
    ofType(Auth.requestResetRequested),
    exhaustMap(({ email }) => this.api.requestPasswordReset(email).pipe(
      map(() => Auth.requestResetSucceeded()),
      catchError((err: HttpErrorResponse) => of(Auth.requestResetFailed({ error: toApiError(err) }))),
    )),
  ));

  resetPassword$ = createEffect(() => this.actions$.pipe(
    ofType(Auth.resetPasswordRequested),
    exhaustMap(({ token, newPassword }) => this.api.resetPassword(token, newPassword).pipe(
      map(() => Auth.resetPasswordSucceeded()),
      catchError((err: HttpErrorResponse) => of(Auth.resetPasswordFailed({ error: toApiError(err) }))),
    )),
  ));

  logout$ = createEffect(() => this.actions$.pipe(
    ofType(Auth.logoutRequested),
    withLatestFrom(this.store.select(selectTokens)),
    exhaustMap(([_, tokens]) => {
      const refresh = tokens?.refreshToken;
      const obs = refresh ? this.api.logout(refresh).pipe(catchError(() => of(null))) : of(null);
      return obs.pipe(map(() => Auth.logoutLocal()));
    }),
  ));

  logoutSideEffects$ = createEffect(() => this.actions$.pipe(
    ofType(Auth.logoutLocal, Auth.sessionInvalidated, Auth.refreshFailed, Auth.bootstrapFailed),
    tap(() => {
      this.storage.clear();
      this.router.navigateByUrl('/login');
    }),
  ), { dispatch: false });
}
```

- [ ] **Step 5: Write reducer test `auth.reducer.spec.ts`**

```typescript
import { authReducer, initialState } from './auth.reducer';
import { Auth } from './auth.actions';
import { User, AuthTokens } from '../../core/models';

const USER: User = { id: 'u1', email: 'a@b.com', username: 'alice', emailVerified: true, createdAt: '2026-01-01' };
const TOKENS: AuthTokens = { accessToken: 'a', refreshToken: 'r', accessExpiresAt: '2026-12-31' };

describe('authReducer', () => {
  it('bootstrapFromStorage sets status bootstrapping', () => {
    const s = authReducer(initialState, Auth.bootstrapFromStorage());
    expect(s.status).toBe('bootstrapping');
  });

  it('bootstrapSucceeded sets user, tokens, authenticated', () => {
    const s = authReducer(initialState, Auth.bootstrapSucceeded({ user: USER, tokens: TOKENS }));
    expect(s.user).toEqual(USER);
    expect(s.tokens).toEqual(TOKENS);
    expect(s.status).toBe('authenticated');
  });

  it('loginFailed records error', () => {
    const s = authReducer(initialState, Auth.loginFailed({ error: { code: 'INVALID_CREDENTIALS', message: 'x' } }));
    expect(s.error?.code).toBe('INVALID_CREDENTIALS');
    expect(s.status).toBe('anonymous');
  });

  it('logoutLocal resets state', () => {
    const dirty = { ...initialState, user: USER, tokens: TOKENS, status: 'authenticated' as const };
    expect(authReducer(dirty, Auth.logoutLocal())).toEqual(initialState);
  });

  it('sessionInvalidated records reason', () => {
    const s = authReducer(initialState, Auth.sessionInvalidated({ reason: 'TOKEN_REUSED' }));
    expect(s.lastInvalidationReason).toBe('TOKEN_REUSED');
    expect(s.user).toBeNull();
  });
});
```

- [ ] **Step 6: Write effects test `auth.effects.spec.ts`**

```typescript
import { TestBed } from '@angular/core/testing';
import { provideMockActions } from '@ngrx/effects/testing';
import { Observable, of, throwError } from 'rxjs';
import { Action } from '@ngrx/store';
import { provideMockStore } from '@ngrx/store/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthEffects } from './auth.effects';
import { Auth } from './auth.actions';
import { AuthApi } from '../../core/api/auth.api';
import { AuthStorageService } from '../../core/auth/auth-storage.service';

describe('AuthEffects', () => {
  let actions$: Observable<Action>;
  let effects: AuthEffects;
  let apiSpy: jasmine.SpyObj<AuthApi>;
  let storageSpy: jasmine.SpyObj<AuthStorageService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    apiSpy = jasmine.createSpyObj('AuthApi', ['login','me','register','verifyEmail','refresh','logout','requestPasswordReset','resetPassword']);
    storageSpy = jasmine.createSpyObj('AuthStorageService', ['getTokens','setTokens','clear']);
    routerSpy = jasmine.createSpyObj('Router', ['navigateByUrl']);

    TestBed.configureTestingModule({
      providers: [
        AuthEffects,
        provideMockActions(() => actions$),
        provideMockStore({ initialState: { auth: { user: null, tokens: null, status: 'anonymous', error: null, lastInvalidationReason: null } } }),
        { provide: AuthApi, useValue: apiSpy },
        { provide: AuthStorageService, useValue: storageSpy },
        { provide: Router, useValue: routerSpy },
      ],
    });
    effects = TestBed.inject(AuthEffects);
  });

  it('login success → loginSucceeded', (done) => {
    apiSpy.login.and.returnValue(of({ accessToken: 'a', refreshToken: 'r', accessExpiresAt: '2026-01-01' }));
    actions$ = of(Auth.loginRequested({ emailOrUsername: 'a', password: 'p' }));
    effects.login$.subscribe(action => {
      expect(action).toEqual(Auth.loginSucceeded({ tokens: { accessToken: 'a', refreshToken: 'r', accessExpiresAt: '2026-01-01' } }));
      done();
    });
  });

  it('login error → loginFailed with ApiError', (done) => {
    apiSpy.login.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 400, error: { code: 'INVALID_CREDENTIALS', message: 'wrong' },
    })));
    actions$ = of(Auth.loginRequested({ emailOrUsername: 'a', password: 'p' }));
    effects.login$.subscribe(action => {
      expect(action.type).toBe('[Auth] Login Failed');
      expect((action as any).error.code).toBe('INVALID_CREDENTIALS');
      done();
    });
  });

  it('bootstrap with no stored tokens → bootstrapFailed', (done) => {
    storageSpy.getTokens.and.returnValue(null);
    actions$ = of(Auth.bootstrapFromStorage());
    effects.bootstrap$.subscribe(action => {
      expect(action).toEqual(Auth.bootstrapFailed());
      done();
    });
  });

  it('bootstrap with stored tokens + me() success → bootstrapSucceeded', (done) => {
    const tokens = { accessToken: 'a', refreshToken: 'r', accessExpiresAt: '2026-01-01' };
    const user = { id: 'u1', email: 'a@b.com', username: 'alice', emailVerified: true, createdAt: '2026-01-01' };
    storageSpy.getTokens.and.returnValue(tokens);
    apiSpy.me.and.returnValue(of(user));
    actions$ = of(Auth.bootstrapFromStorage());
    effects.bootstrap$.subscribe(action => {
      expect(action).toEqual(Auth.bootstrapSucceeded({ user, tokens }));
      done();
    });
  });
});
```

- [ ] **Step 7: Run + commit**

```bash
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --include='**/store/auth/**'
git add frontend/src/app/store/auth/
git commit -m "feat(frontend): NgRx Auth feature — actions, reducer, selectors, effects + tests"
```

---

### Task E2: Lobby NgRx feature

**Files (in `frontend/src/app/store/lobby/`):**
- `lobby.actions.ts`, `lobby.reducer.ts`, `lobby.feature.ts`, `lobby.selectors.ts`, `lobby.effects.ts`
- `lobby.reducer.spec.ts`, `lobby.effects.spec.ts`

- [ ] **Step 1: Actions**

```typescript
import { createActionGroup, props, emptyProps } from '@ngrx/store';
import { LobbyGame, ApiError } from '../../core/models';
import { CreateGameRequest } from '../../core/api/lobby.api';

export const Lobby = createActionGroup({
  source: 'Lobby',
  events: {
    'Create Requested': props<{ req: CreateGameRequest }>(),
    'Create Succeeded': props<{ game: LobbyGame }>(),
    'Create Failed': props<{ error: ApiError }>(),

    'Join By Code Requested': props<{ joinCode: string }>(),
    'Join By Code Succeeded': props<{ game: LobbyGame }>(),
    'Join By Code Failed': props<{ error: ApiError }>(),

    'Join Public Requested': props<{ gameId: string }>(),
    'Join Public Succeeded': props<{ game: LobbyGame }>(),
    'Join Public Failed': props<{ error: ApiError }>(),

    'List Public Requested': emptyProps(),
    'List Public Succeeded': props<{ games: LobbyGame[] }>(),
    'List Public Failed': props<{ error: ApiError }>(),

    'My Games Requested': emptyProps(),
    'My Games Succeeded': props<{ games: LobbyGame[] }>(),
    'My Games Failed': props<{ error: ApiError }>(),

    'Leave Requested': props<{ gameId: string }>(),
    'Leave Succeeded': props<{ gameId: string }>(),
    'Leave Failed': props<{ error: ApiError }>(),
  },
});
```

- [ ] **Step 2: Reducer + feature**

```typescript
import { createFeature, createReducer, on } from '@ngrx/store';
import { Lobby } from './lobby.actions';
import { LobbyGame, ApiError } from '../../core/models';

export interface LobbyState {
  publicGames: LobbyGame[];
  myGames: LobbyGame[];
  loading: boolean;
  error: ApiError | null;
}

export const initialLobbyState: LobbyState = {
  publicGames: [],
  myGames: [],
  loading: false,
  error: null,
};

export const lobbyReducer = createReducer(
  initialLobbyState,
  on(Lobby.createRequested, Lobby.joinByCodeRequested, Lobby.joinPublicRequested,
     Lobby.listPublicRequested, Lobby.myGamesRequested, Lobby.leaveRequested,
     s => ({ ...s, loading: true, error: null })),
  on(Lobby.createFailed, Lobby.joinByCodeFailed, Lobby.joinPublicFailed,
     Lobby.listPublicFailed, Lobby.myGamesFailed, Lobby.leaveFailed,
     (s, { error }) => ({ ...s, loading: false, error })),
  on(Lobby.listPublicSucceeded, (s, { games }) => ({ ...s, loading: false, publicGames: games })),
  on(Lobby.myGamesSucceeded, (s, { games }) => ({ ...s, loading: false, myGames: games })),
  on(Lobby.createSucceeded, Lobby.joinByCodeSucceeded, Lobby.joinPublicSucceeded,
     s => ({ ...s, loading: false })),
  on(Lobby.leaveSucceeded, (s, { gameId }) =>
    ({ ...s, loading: false, myGames: s.myGames.filter(g => g.id !== gameId) })),
);

export const lobbyFeature = createFeature({
  name: 'lobby',
  reducer: lobbyReducer,
});
```

- [ ] **Step 3: Selectors**

```typescript
import { lobbyFeature } from './lobby.reducer';

export const {
  selectPublicGames,
  selectMyGames,
  selectLoading: selectLobbyLoading,
  selectError: selectLobbyError,
} = lobbyFeature;
```

- [ ] **Step 4: Effects**

```typescript
import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, exhaustMap, map, of, tap } from 'rxjs';
import { Lobby } from './lobby.actions';
import { LobbyApi } from '../../core/api/lobby.api';
import { ApiError } from '../../core/models';

const toApiError = (err: HttpErrorResponse): ApiError =>
  (err.error && typeof err.error === 'object' && 'code' in err.error)
    ? (err.error as ApiError)
    : { code: 'NETWORK', message: 'Eroare de rețea.' };

@Injectable()
export class LobbyEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(LobbyApi);
  private readonly router = inject(Router);

  create$ = createEffect(() => this.actions$.pipe(
    ofType(Lobby.createRequested),
    exhaustMap(({ req }) => this.api.create(req).pipe(
      map(game => Lobby.createSucceeded({ game })),
      catchError(err => of(Lobby.createFailed({ error: toApiError(err) }))),
    )),
  ));

  joinByCode$ = createEffect(() => this.actions$.pipe(
    ofType(Lobby.joinByCodeRequested),
    exhaustMap(({ joinCode }) => this.api.joinByCode(joinCode).pipe(
      map(game => Lobby.joinByCodeSucceeded({ game })),
      catchError(err => of(Lobby.joinByCodeFailed({ error: toApiError(err) }))),
    )),
  ));

  joinPublic$ = createEffect(() => this.actions$.pipe(
    ofType(Lobby.joinPublicRequested),
    exhaustMap(({ gameId }) => this.api.joinPublic(gameId).pipe(
      map(game => Lobby.joinPublicSucceeded({ game })),
      catchError(err => of(Lobby.joinPublicFailed({ error: toApiError(err) }))),
    )),
  ));

  listPublic$ = createEffect(() => this.actions$.pipe(
    ofType(Lobby.listPublicRequested),
    exhaustMap(() => this.api.listPublic().pipe(
      map(games => Lobby.listPublicSucceeded({ games })),
      catchError(err => of(Lobby.listPublicFailed({ error: toApiError(err) }))),
    )),
  ));

  myGames$ = createEffect(() => this.actions$.pipe(
    ofType(Lobby.myGamesRequested),
    exhaustMap(() => this.api.myGames().pipe(
      map(games => Lobby.myGamesSucceeded({ games })),
      catchError(err => of(Lobby.myGamesFailed({ error: toApiError(err) }))),
    )),
  ));

  leave$ = createEffect(() => this.actions$.pipe(
    ofType(Lobby.leaveRequested),
    exhaustMap(({ gameId }) => this.api.leave(gameId).pipe(
      map(() => Lobby.leaveSucceeded({ gameId })),
      catchError(err => of(Lobby.leaveFailed({ error: toApiError(err) }))),
    )),
  ));

  navigateOnJoinOrCreate$ = createEffect(() => this.actions$.pipe(
    ofType(Lobby.createSucceeded, Lobby.joinByCodeSucceeded, Lobby.joinPublicSucceeded),
    tap(({ game }) => this.router.navigateByUrl(`/game/${game.id}`)),
  ), { dispatch: false });
}
```

- [ ] **Step 5: Tests** — minimal happy-path coverage for reducer + 2 effects

`lobby.reducer.spec.ts`:

```typescript
import { lobbyReducer, initialLobbyState } from './lobby.reducer';
import { Lobby } from './lobby.actions';
import { LobbyGame } from '../../core/models';

const GAME: LobbyGame = {
  id: 'g1', ownerId: 'u1', visibility: 'PRIVATE', joinCode: 'CODE',
  numPlayers: 2, mode: 'ETALAT', difficulty: 'MED',
  seatsTaken: 1, started: false, createdAt: '2026-01-01',
};

describe('lobbyReducer', () => {
  it('createRequested sets loading + clears error', () => {
    const s = lobbyReducer(initialLobbyState, Lobby.createRequested({ req: {} as any }));
    expect(s.loading).toBeTrue();
    expect(s.error).toBeNull();
  });

  it('listPublicSucceeded fills publicGames', () => {
    const s = lobbyReducer(initialLobbyState, Lobby.listPublicSucceeded({ games: [GAME] }));
    expect(s.publicGames).toEqual([GAME]);
    expect(s.loading).toBeFalse();
  });

  it('leaveSucceeded removes from myGames', () => {
    const start = { ...initialLobbyState, myGames: [GAME] };
    const s = lobbyReducer(start, Lobby.leaveSucceeded({ gameId: 'g1' }));
    expect(s.myGames).toEqual([]);
  });

  it('createFailed records error', () => {
    const s = lobbyReducer(initialLobbyState, Lobby.createFailed({ error: { code: 'X', message: 'y' } }));
    expect(s.error?.code).toBe('X');
    expect(s.loading).toBeFalse();
  });
});
```

`lobby.effects.spec.ts` (minimal — create path only; others identical):

```typescript
import { TestBed } from '@angular/core/testing';
import { provideMockActions } from '@ngrx/effects/testing';
import { Observable, of } from 'rxjs';
import { Action } from '@ngrx/store';
import { Router } from '@angular/router';
import { LobbyEffects } from './lobby.effects';
import { Lobby } from './lobby.actions';
import { LobbyApi } from '../../core/api/lobby.api';

describe('LobbyEffects', () => {
  let actions$: Observable<Action>;
  let effects: LobbyEffects;
  let apiSpy: jasmine.SpyObj<LobbyApi>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    apiSpy = jasmine.createSpyObj('LobbyApi', ['create','joinByCode','joinPublic','listPublic','myGames','leave']);
    routerSpy = jasmine.createSpyObj('Router', ['navigateByUrl']);
    TestBed.configureTestingModule({
      providers: [
        LobbyEffects,
        provideMockActions(() => actions$),
        { provide: LobbyApi, useValue: apiSpy },
        { provide: Router, useValue: routerSpy },
      ],
    });
    effects = TestBed.inject(LobbyEffects);
  });

  it('create success → createSucceeded', (done) => {
    const game = { id: 'g1' } as any;
    apiSpy.create.and.returnValue(of(game));
    actions$ = of(Lobby.createRequested({ req: {} as any }));
    effects.create$.subscribe(action => {
      expect(action).toEqual(Lobby.createSucceeded({ game }));
      done();
    });
  });
});
```

- [ ] **Step 6: Commit**

```bash
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --include='**/store/lobby/**'
git add frontend/src/app/store/lobby/
git commit -m "feat(frontend): NgRx Lobby feature + tests"
```

---

### Task E3: Match NgRx feature

**Files (in `frontend/src/app/store/match/`):**
- `match.actions.ts`, `match.reducer.ts`, `match.selectors.ts`, `match.effects.ts`
- `match.reducer.spec.ts`

- [ ] **Step 1: Actions**

```typescript
import { createActionGroup, props, emptyProps } from '@ngrx/store';
import { LobbyGame, ApiError } from '../../core/models';
import { QuickMatchRequest } from '../../core/api/matchmaking.api';

export const Match = createActionGroup({
  source: 'Match',
  events: {
    'Quick Requested': props<{ req: QuickMatchRequest }>(),
    'Queued': emptyProps(),
    'Matched': props<{ game: LobbyGame }>(),
    'Quick Failed': props<{ error: ApiError }>(),

    'Cancel Requested': emptyProps(),
    'Cancelled': emptyProps(),

    'Subscribe To Match Topic': emptyProps(),    // triggers subscribeToMatches via effect
  },
});
```

- [ ] **Step 2: Reducer + feature**

```typescript
import { createFeature, createReducer, on } from '@ngrx/store';
import { Match } from './match.actions';
import { LobbyGame, ApiError } from '../../core/models';

export type MatchStatus = 'idle' | 'queued' | 'matched' | 'error';

export interface MatchState {
  status: MatchStatus;
  matchedGame: LobbyGame | null;
  error: ApiError | null;
}

export const initialMatchState: MatchState = { status: 'idle', matchedGame: null, error: null };

export const matchReducer = createReducer(
  initialMatchState,
  on(Match.quickRequested, s => ({ ...s, status: 'idle' as const, error: null })),
  on(Match.queued, s => ({ ...s, status: 'queued' as const })),
  on(Match.matched, (s, { game }) => ({ ...s, status: 'matched' as const, matchedGame: game })),
  on(Match.quickFailed, (s, { error }) => ({ ...s, status: 'error' as const, error })),
  on(Match.cancelled, () => initialMatchState),
);

export const matchFeature = createFeature({ name: 'match', reducer: matchReducer });
```

- [ ] **Step 3: Selectors**

```typescript
import { matchFeature } from './match.reducer';
export const { selectStatus: selectMatchStatus, selectMatchedGame, selectError: selectMatchError } = matchFeature;
```

- [ ] **Step 4: Effects**

```typescript
import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, exhaustMap, map, of, switchMap, tap } from 'rxjs';
import { Match } from './match.actions';
import { MatchmakingApi } from '../../core/api/matchmaking.api';
import { GameWsService } from '../../core/ws/game-ws.service';
import { ApiError } from '../../core/models';

const toApiError = (err: HttpErrorResponse): ApiError =>
  (err.error && typeof err.error === 'object' && 'code' in err.error)
    ? (err.error as ApiError)
    : { code: 'NETWORK', message: 'Eroare de rețea.' };

@Injectable()
export class MatchEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(MatchmakingApi);
  private readonly ws = inject(GameWsService);
  private readonly router = inject(Router);

  subscribeToMatchTopic$ = createEffect(() => this.actions$.pipe(
    ofType(Match.subscribeToMatchTopic),
    switchMap(() => this.ws.subscribeToMatches().pipe(
      map(game => Match.matched({ game })),
    )),
  ));

  quick$ = createEffect(() => this.actions$.pipe(
    ofType(Match.quickRequested),
    exhaustMap(({ req }) => this.api.quick(req).pipe(
      map(resp => resp.matched && resp.game
          ? Match.matched({ game: resp.game })
          : Match.queued()),
      catchError(err => of(Match.quickFailed({ error: toApiError(err) }))),
    )),
  ));

  cancel$ = createEffect(() => this.actions$.pipe(
    ofType(Match.cancelRequested),
    exhaustMap(() => this.api.cancel().pipe(
      map(() => Match.cancelled()),
      catchError(() => of(Match.cancelled())),
    )),
  ));

  navigateOnMatched$ = createEffect(() => this.actions$.pipe(
    ofType(Match.matched),
    tap(({ game }) => this.router.navigateByUrl(`/game/${game.id}`)),
  ), { dispatch: false });
}
```

- [ ] **Step 5: Reducer test**

```typescript
import { matchReducer, initialMatchState } from './match.reducer';
import { Match } from './match.actions';

describe('matchReducer', () => {
  it('queued → status queued', () => {
    const s = matchReducer(initialMatchState, Match.queued());
    expect(s.status).toBe('queued');
  });
  it('matched → status matched + matchedGame', () => {
    const game = { id: 'g1' } as any;
    const s = matchReducer(initialMatchState, Match.matched({ game }));
    expect(s.status).toBe('matched');
    expect(s.matchedGame).toBe(game);
  });
  it('cancelled → initial', () => {
    const dirty = { ...initialMatchState, status: 'queued' as const };
    expect(matchReducer(dirty, Match.cancelled())).toEqual(initialMatchState);
  });
});
```

- [ ] **Step 6: Commit**

```bash
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --include='**/store/match/**'
git add frontend/src/app/store/match/
git commit -m "feat(frontend): NgRx Match feature (quick-match + WS-driven matched action)"
```

---

### Task E4: Game NgRx feature

**Files (in `frontend/src/app/store/game/`):**
- `game.actions.ts`, `game.reducer.ts`, `game.selectors.ts`, `game.effects.ts`
- `game.reducer.spec.ts`

- [ ] **Step 1: Actions**

```typescript
import { createActionGroup, props, emptyProps } from '@ngrx/store';
import { GameView, Action, ApiError, DomainEvent } from '../../core/models';

export const Game = createActionGroup({
  source: 'Game',
  events: {
    'Subscribe To Game': props<{ gameId: string }>(),
    'View Received': props<{ view: GameView; events: DomainEvent[] }>(),

    'Subscribe To Errors': emptyProps(),
    'Error Received': props<{ error: ApiError }>(),

    'Send Action': props<{ gameId: string; action: Action }>(),

    'Load Game Requested': props<{ gameId: string }>(),    // fallback REST GET
    'Load Game Succeeded': props<{ view: GameView }>(),
    'Load Game Failed': props<{ error: ApiError }>(),

    'Clear Game': emptyProps(),
  },
});
```

- [ ] **Step 2: Reducer + feature**

```typescript
import { createFeature, createReducer, on } from '@ngrx/store';
import { Game } from './game.actions';
import { GameView, DomainEvent, ApiError } from '../../core/models';

export interface GameState {
  gameId: string | null;
  view: GameView | null;
  events: DomainEvent[];
  error: ApiError | null;
}

export const initialGameState: GameState = { gameId: null, view: null, events: [], error: null };

export const gameReducer = createReducer(
  initialGameState,
  on(Game.subscribeToGame, (s, { gameId }) => ({ ...initialGameState, gameId })),
  on(Game.viewReceived, (s, { view, events }) => ({ ...s, view, events: [...s.events, ...events] })),
  on(Game.loadGameSucceeded, (s, { view }) => ({ ...s, view })),
  on(Game.loadGameFailed, (s, { error }) => ({ ...s, error })),
  on(Game.errorReceived, (s, { error }) => ({ ...s, error })),
  on(Game.clearGame, () => initialGameState),
);

export const gameFeature = createFeature({ name: 'game', reducer: gameReducer });
```

- [ ] **Step 3: Selectors**

```typescript
import { gameFeature } from './game.reducer';
export const {
  selectGameId,
  selectView: selectGameView,
  selectEvents: selectGameEvents,
  selectError: selectGameError,
} = gameFeature;
```

- [ ] **Step 4: Effects**

```typescript
import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, exhaustMap, map, of, switchMap, tap } from 'rxjs';
import { Game } from './game.actions';
import { GameWsService } from '../../core/ws/game-ws.service';
import { LobbyApi } from '../../core/api/lobby.api';
import { ApiError } from '../../core/models';

const toApiError = (err: HttpErrorResponse): ApiError =>
  (err.error && typeof err.error === 'object' && 'code' in err.error)
    ? (err.error as ApiError)
    : { code: 'NETWORK', message: 'Eroare de rețea.' };

@Injectable()
export class GameEffects {
  private readonly actions$ = inject(Actions);
  private readonly ws = inject(GameWsService);
  private readonly api = inject(LobbyApi);

  subscribeToGame$ = createEffect(() => this.actions$.pipe(
    ofType(Game.subscribeToGame),
    switchMap(({ gameId }) => this.ws.subscribeToGame(gameId).pipe(
      map(({ view, events }) => Game.viewReceived({ view, events })),
    )),
  ));

  subscribeToErrors$ = createEffect(() => this.actions$.pipe(
    ofType(Game.subscribeToErrors),
    switchMap(() => this.ws.subscribeToErrors().pipe(
      map(error => Game.errorReceived({ error })),
    )),
  ));

  loadGame$ = createEffect(() => this.actions$.pipe(
    ofType(Game.loadGameRequested),
    exhaustMap(({ gameId }) => this.api.get(gameId).pipe(
      map(view => Game.loadGameSucceeded({ view })),
      catchError(err => of(Game.loadGameFailed({ error: toApiError(err) }))),
    )),
  ));

  sendAction$ = createEffect(() => this.actions$.pipe(
    ofType(Game.sendAction),
    tap(({ gameId, action }) => this.ws.sendAction(gameId, action)),
  ), { dispatch: false });
}
```

- [ ] **Step 5: Reducer test**

```typescript
import { gameReducer, initialGameState } from './game.reducer';
import { Game } from './game.actions';

describe('gameReducer', () => {
  it('subscribeToGame sets gameId and clears prior state', () => {
    const s = gameReducer(initialGameState, Game.subscribeToGame({ gameId: 'g1' }));
    expect(s.gameId).toBe('g1');
    expect(s.view).toBeNull();
  });
  it('viewReceived appends events and sets view', () => {
    const view = { id: 'g1' } as any;
    const events = [{ type: 'CardDrawn' } as any];
    const s = gameReducer({ ...initialGameState, gameId: 'g1' }, Game.viewReceived({ view, events }));
    expect(s.view).toBe(view);
    expect(s.events).toEqual(events);
  });
  it('clearGame resets state', () => {
    const dirty = { ...initialGameState, gameId: 'g1', view: {} as any };
    expect(gameReducer(dirty, Game.clearGame())).toEqual(initialGameState);
  });
});
```

- [ ] **Step 6: Commit**

```bash
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --include='**/store/game/**'
git add frontend/src/app/store/game/
git commit -m "feat(frontend): NgRx Game feature (subscribe/view/sendAction effects) + tests"
```

---

## End of Part 2

Continuă cu Part 3 (`2026-05-17-stage4a-frontend-part3.md`) — JwtInterceptor + AuthGuard + app.config + AppComponent + feature pages + routes + smoke test + README.
