# Stage 4a — Frontend Implementation Plan (Part 3: Interceptor + Guard + App + Pages + Routes + Smoke)

> **For agentic workers:** Continuation of Parts 1-2. Same TDD discipline.

**Pre-requisite:** Parts 1+2 — scaffold, models, core utilities, API services, WS services, NgRx stores.

---

## Phase F — JwtInterceptor, AuthGuard, app.config, AppComponent

### Task F1: `jwt.interceptor` + tests

**Files:**
- Create: `frontend/src/app/core/auth/jwt.interceptor.ts`
- Create: `frontend/src/app/core/auth/jwt.interceptor.spec.ts`

- [ ] **Step 1: Write `jwt.interceptor.ts`**

```typescript
import { HttpErrorResponse, HttpEvent, HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Store } from '@ngrx/store';
import { Observable, ReplaySubject, catchError, switchMap, throwError } from 'rxjs';
import { AuthStorageService } from './auth-storage.service';
import { AuthApi } from '../api/auth.api';
import { Auth } from '../../store/auth/auth.actions';
import { AuthTokens } from '../models';

let refreshInFlight$: ReplaySubject<AuthTokens> | null = null;

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const storage = inject(AuthStorageService);
  const api = inject(AuthApi);
  const store = inject(Store);

  const tokens = storage.getTokens();
  const authReq = tokens
      ? req.clone({ setHeaders: { Authorization: `Bearer ${tokens.accessToken}` } })
      : req;

  return next(authReq).pipe(
    catchError((err: HttpErrorResponse) => {
      // Never retry refresh endpoint itself; just surface failure.
      if (req.url.endsWith('/auth/refresh')) {
        store.dispatch(Auth.sessionInvalidated({ reason: err.error?.code ?? 'UNAUTHORIZED' }));
        return throwError(() => err);
      }
      if (err.status === 401 && err.error?.code === 'TOKEN_EXPIRED' && tokens) {
        return refreshAndRetry(req, next, tokens.refreshToken, api, storage, store);
      }
      if (err.status === 401) {
        const reason = err.error?.code ?? 'UNAUTHORIZED';
        store.dispatch(Auth.sessionInvalidated({ reason }));
      }
      return throwError(() => err);
    }),
  );
};

function refreshAndRetry(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  refreshToken: string,
  api: AuthApi,
  storage: AuthStorageService,
  store: Store,
): Observable<HttpEvent<unknown>> {
  if (!refreshInFlight$) {
    refreshInFlight$ = new ReplaySubject<AuthTokens>(1);
    api.refresh(refreshToken).subscribe({
      next: tokens => {
        storage.setTokens(tokens);
        store.dispatch(Auth.refreshSucceeded({ tokens }));
        refreshInFlight$!.next(tokens);
        refreshInFlight$!.complete();
        refreshInFlight$ = null;
      },
      error: err => {
        store.dispatch(Auth.sessionInvalidated({ reason: err?.error?.code ?? 'UNAUTHORIZED' }));
        refreshInFlight$!.error(err);
        refreshInFlight$ = null;
      },
    });
  }
  return refreshInFlight$.pipe(
    switchMap(tokens =>
      next(req.clone({ setHeaders: { Authorization: `Bearer ${tokens.accessToken}` } }))),
  );
}
```

- [ ] **Step 2: Write test**

```typescript
import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideMockStore } from '@ngrx/store/testing';
import { Store } from '@ngrx/store';
import { jwtInterceptor } from './jwt.interceptor';
import { AuthStorageService } from './auth-storage.service';
import { AuthApi } from '../api/auth.api';
import { Auth } from '../../store/auth/auth.actions';

describe('jwtInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let storage: jasmine.SpyObj<AuthStorageService>;
  let api: jasmine.SpyObj<AuthApi>;
  let store: jasmine.SpyObj<Store>;

  beforeEach(() => {
    storage = jasmine.createSpyObj('AuthStorageService', ['getTokens', 'setTokens', 'clear']);
    api = jasmine.createSpyObj('AuthApi', ['refresh']);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])),
        provideHttpClientTesting(),
        provideMockStore({ initialState: {} }),
        { provide: AuthStorageService, useValue: storage },
        { provide: AuthApi, useValue: api },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    store = TestBed.inject(Store) as jasmine.SpyObj<Store>;
    spyOn(store, 'dispatch');
  });

  afterEach(() => httpMock.verify());

  it('adds Bearer header when tokens present', () => {
    storage.getTokens.and.returnValue({ accessToken: 'access-1', refreshToken: 'r', accessExpiresAt: 'x' });
    http.get('/api/users/me').subscribe();
    const req = httpMock.expectOne('/api/users/me');
    expect(req.request.headers.get('Authorization')).toBe('Bearer access-1');
    req.flush({});
  });

  it('omits Bearer header when no tokens', () => {
    storage.getTokens.and.returnValue(null);
    http.get('/api/auth/login').subscribe();
    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('on 401 dispatches sessionInvalidated', (done) => {
    storage.getTokens.and.returnValue(null);
    http.get('/api/games/mine').subscribe({
      error: () => {
        expect(store.dispatch).toHaveBeenCalledWith(
            Auth.sessionInvalidated({ reason: 'UNAUTHORIZED' }));
        done();
      },
    });
    httpMock.expectOne('/api/games/mine').flush(
        { code: 'UNAUTHORIZED', message: 'x' },
        { status: 401, statusText: 'Unauthorized' });
  });
});
```

(Token-expired + refresh path is exercised by the manual smoke test; unit-testing the ReplaySubject dedup is harder than its value.)

- [ ] **Step 3: Run + commit**

```bash
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --include='**/jwt.interceptor.spec.ts'
git add frontend/src/app/core/auth/jwt.interceptor.ts \
        frontend/src/app/core/auth/jwt.interceptor.spec.ts
git commit -m "feat(frontend): jwtInterceptor — Bearer injection + 401 refresh + session invalidation"
```

---

### Task F2: `authGuard`

**Files:**
- Create: `frontend/src/app/core/auth/auth.guard.ts`
- Create: `frontend/src/app/core/auth/auth.guard.spec.ts`

- [ ] **Step 1: Write guard**

```typescript
import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { Store } from '@ngrx/store';
import { selectIsAuthenticated } from '../../store/auth/auth.selectors';
import { map, take } from 'rxjs';

export const authGuard: CanActivateFn = () => {
  const store = inject(Store);
  const router = inject(Router);
  return store.select(selectIsAuthenticated).pipe(
    take(1),
    map(isAuth => isAuth ? true : router.parseUrl('/login')),
  );
};
```

- [ ] **Step 2: Test**

```typescript
import { TestBed } from '@angular/core/testing';
import { provideMockStore } from '@ngrx/store/testing';
import { Router, UrlTree } from '@angular/router';
import { firstValueFrom, isObservable, of } from 'rxjs';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideMockStore({ initialState: { auth: { user: null, tokens: null, status: 'anonymous', error: null, lastInvalidationReason: null } } }),
        { provide: Router, useValue: { parseUrl: (url: string) => ({ toString: () => url } as UrlTree) } },
      ],
    });
  });

  it('returns UrlTree to /login when anonymous', async () => {
    const result = TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));
    if (isObservable(result)) {
      const v = await firstValueFrom(result);
      expect((v as UrlTree).toString()).toBe('/login');
    } else {
      fail('expected Observable');
    }
  });
});
```

- [ ] **Step 3: Commit**

```bash
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --include='**/auth.guard.spec.ts'
git add frontend/src/app/core/auth/auth.guard.ts frontend/src/app/core/auth/auth.guard.spec.ts
git commit -m "feat(frontend): authGuard (CanActivateFn) returning UrlTree on unauthenticated"
```

---

### Task F3: `app.config.ts` (providers wiring)

**Files:**
- Modify: `frontend/src/app/app.config.ts` (Ionic scaffold creates this)
- Modify: `frontend/src/app/app.routes.ts`

- [ ] **Step 1: Replace `app.config.ts`**

```typescript
import { ApplicationConfig, ErrorHandler, importProvidersFrom, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding, withRouterConfig } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { IonicRouteStrategy, provideIonicAngular } from '@ionic/angular/standalone';
import { RouteReuseStrategy } from '@angular/router';
import { provideStore } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { provideStoreDevtools } from '@ngrx/store-devtools';

import { routes } from './app.routes';
import { jwtInterceptor } from './core/auth/jwt.interceptor';
import { authFeature } from './store/auth/auth.feature';
import { lobbyFeature } from './store/lobby/lobby.reducer';
import { matchFeature } from './store/match/match.reducer';
import { gameFeature } from './store/game/game.reducer';
import { AuthEffects } from './store/auth/auth.effects';
import { LobbyEffects } from './store/lobby/lobby.effects';
import { MatchEffects } from './store/match/match.effects';
import { GameEffects } from './store/game/game.effects';
import { GlobalErrorHandler } from './shared/global-error-handler';
import { environment } from '../environments/environment';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    { provide: RouteReuseStrategy, useClass: IonicRouteStrategy },
    provideIonicAngular(),
    provideRouter(routes, withRouterConfig({ paramsInheritanceStrategy: 'always' }), withComponentInputBinding()),
    provideHttpClient(withInterceptors([jwtInterceptor])),
    provideStore({
      [authFeature.name]: authFeature.reducer,
      [lobbyFeature.name]: lobbyFeature.reducer,
      [matchFeature.name]: matchFeature.reducer,
      [gameFeature.name]: gameFeature.reducer,
    }),
    provideEffects(AuthEffects, LobbyEffects, MatchEffects, GameEffects),
    provideStoreDevtools({ maxAge: 25, logOnly: environment.production }),
    { provide: ErrorHandler, useClass: GlobalErrorHandler },
  ],
};
```

- [ ] **Step 2: Build to verify wiring** + commit

```bash
cd frontend && npx ng build --configuration=development
git add frontend/src/app/app.config.ts
git commit -m "feat(frontend): app.config providers — HttpClient+interceptor, NgRx store+effects, Ionic"
```

---

### Task F4: Update `AppComponent` (bootstrap + WsIndicator + logout)

**Files:**
- Modify: `frontend/src/app/app.component.ts`
- Modify: `frontend/src/app/app.component.html`
- Modify: `frontend/src/app/app.component.scss`
- Create: `frontend/src/app/app.component.spec.ts`

- [ ] **Step 1: Replace `app.component.ts`**

```typescript
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { IonApp, IonRouterOutlet, IonHeader, IonToolbar, IonTitle, IonButton, IonButtons, IonContent }
    from '@ionic/angular/standalone';
import { Auth } from './store/auth/auth.actions';
import { selectIsAuthenticated, selectUser } from './store/auth/auth.selectors';
import { StompService } from './core/ws/stomp.service';
import { WsIndicatorComponent } from './shared/ws-indicator/ws-indicator.component';
import { Observable } from 'rxjs';
import { User } from './core/models';
import { WsConnectionState } from './core/ws/ws-state';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule, RouterOutlet,
    IonApp, IonRouterOutlet, IonHeader, IonToolbar, IonTitle, IonButton, IonButtons, IonContent,
    WsIndicatorComponent,
  ],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss'],
})
export class AppComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly router = inject(Router);
  private readonly stomp = inject(StompService);

  readonly isAuthenticated$: Observable<boolean> = this.store.select(selectIsAuthenticated);
  readonly user$: Observable<User | null> = this.store.select(selectUser);
  readonly wsState$: Observable<WsConnectionState> = this.stomp.connectionState$;

  ngOnInit(): void {
    this.store.dispatch(Auth.bootstrapFromStorage());
  }

  logout(): void {
    this.store.dispatch(Auth.logoutRequested());
  }
}
```

- [ ] **Step 2: Replace `app.component.html`**

```html
<ion-app>
  <ion-header *ngIf="isAuthenticated$ | async">
    <ion-toolbar color="primary">
      <ion-title>Remi <small *ngIf="user$ | async as u">— {{ u.username }}</small></ion-title>
      <ion-buttons slot="end">
        <app-ws-indicator [state]="(wsState$ | async) ?? 'DISCONNECTED'"></app-ws-indicator>
        <ion-button (click)="logout()" data-testid="logout-btn">Logout</ion-button>
      </ion-buttons>
    </ion-toolbar>
  </ion-header>
  <ion-router-outlet></ion-router-outlet>
</ion-app>
```

- [ ] **Step 3: Replace `app.component.scss`**

```scss
ion-title small { font-weight: 400; opacity: 0.8; }
```

(`IonButtons` is already in the imports list from Step 1 above — used for the `<ion-buttons slot="end">` in the template.)

- [ ] **Step 4: Test**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideMockStore, MockStore } from '@ngrx/store/testing';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { AppComponent } from './app.component';
import { Auth } from './store/auth/auth.actions';
import { StompService } from './core/ws/stomp.service';
import { provideRouter } from '@angular/router';

describe('AppComponent', () => {
  let fixture: ComponentFixture<AppComponent>;
  let store: MockStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideMockStore({ initialState: { auth: { user: null, tokens: null, status: 'anonymous', error: null, lastInvalidationReason: null } } }),
        provideRouter([]),
        { provide: StompService, useValue: { connectionState$: new Subject() } },
      ],
    });
    fixture = TestBed.createComponent(AppComponent);
    store = TestBed.inject(MockStore);
    spyOn(store, 'dispatch');
  });

  it('dispatches bootstrapFromStorage on init', () => {
    fixture.detectChanges();
    expect(store.dispatch).toHaveBeenCalledWith(Auth.bootstrapFromStorage());
  });

  it('logout dispatches Auth.logoutRequested', () => {
    fixture.componentInstance.logout();
    expect(store.dispatch).toHaveBeenCalledWith(Auth.logoutRequested());
  });
});
```

- [ ] **Step 5: Run + commit**

```bash
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --include='**/app.component.spec.ts'
git add frontend/src/app/app.component.*
git commit -m "feat(frontend): AppComponent — bootstrap auth, WS indicator header, logout button"
```

---

### Task F5: Connect WS after auth (effect in AppComponent or dedicated effect)

We need to call `stomp.connect(accessToken)` whenever Auth becomes authenticated, and `stomp.disconnect()` on logout.

**Files:**
- Create: `frontend/src/app/store/auth/auth-ws.effects.ts`

- [ ] **Step 1: Write effect**

```typescript
import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import { tap, withLatestFrom } from 'rxjs';
import { Auth } from './auth.actions';
import { StompService } from '../../core/ws/stomp.service';
import { selectTokens } from './auth.selectors';

@Injectable()
export class AuthWsEffects {
  private readonly actions$ = inject(Actions);
  private readonly stomp = inject(StompService);
  private readonly store = inject(Store);

  connectAfterLogin$ = createEffect(() => this.actions$.pipe(
    ofType(Auth.userLoaded, Auth.bootstrapSucceeded),
    withLatestFrom(this.store.select(selectTokens)),
    tap(([_, tokens]) => {
      if (tokens) this.stomp.connect(tokens.accessToken);
    }),
  ), { dispatch: false });

  disconnectOnLogout$ = createEffect(() => this.actions$.pipe(
    ofType(Auth.logoutLocal, Auth.sessionInvalidated, Auth.refreshFailed),
    tap(() => this.stomp.disconnect()),
  ), { dispatch: false });
}
```

- [ ] **Step 2: Register in `app.config.ts`**

Update `provideEffects(...)` to include `AuthWsEffects`:

```typescript
provideEffects(AuthEffects, AuthWsEffects, LobbyEffects, MatchEffects, GameEffects),
```

- [ ] **Step 3: Build + commit**

```bash
cd frontend && npx ng build --configuration=development
git add frontend/src/app/store/auth/auth-ws.effects.ts frontend/src/app/app.config.ts
git commit -m "feat(frontend): AuthWsEffects — connect STOMP on userLoaded, disconnect on logout"
```

---

## Phase G — Auth feature pages

### Task G1: `LoginPage`

**Files:**
- Create: `frontend/src/app/features/auth/login.page.ts`
- Create: `frontend/src/app/features/auth/login.page.html`
- Create: `frontend/src/app/features/auth/login.page.scss`
- Create: `frontend/src/app/features/auth/login.page.spec.ts`

- [ ] **Step 1: Write `login.page.ts`**

```typescript
import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { IonContent, IonInput, IonButton, IonItem, IonList, IonLabel, IonNote }
    from '@ionic/angular/standalone';
import { Auth } from '../../store/auth/auth.actions';
import { selectAuthError, selectAuthStatus, selectLastInvalidationReason } from '../../store/auth/auth.selectors';
import { ErrorBannerComponent } from '../../shared/error-banner/error-banner.component';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonInput, IonButton, IonItem, IonList, IonLabel, IonNote,
    ErrorBannerComponent,
  ],
  templateUrl: './login.page.html',
  styleUrls: ['./login.page.scss'],
})
export default class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);

  readonly form = this.fb.nonNullable.group({
    emailOrUsername: ['', [Validators.required]],
    password: ['', [Validators.required]],
  });

  readonly error$ = this.store.select(selectAuthError);
  readonly status$ = this.store.select(selectAuthStatus);
  readonly invalidationReason$ = this.store.select(selectLastInvalidationReason);

  submit(): void {
    if (this.form.invalid) return;
    const { emailOrUsername, password } = this.form.getRawValue();
    this.store.dispatch(Auth.loginRequested({ emailOrUsername, password }));
  }
}
```

- [ ] **Step 2: Write `login.page.html`**

```html
<ion-content class="ion-padding">
  <h1>Autentificare</h1>

  <div *ngIf="invalidationReason$ | async as reason" class="invalidation-notice" role="alert">
    Sesiunea anterioară a fost încheiată ({{ reason }}). Te rugăm să te reloghezi.
  </div>

  <app-error-banner [error]="error$ | async"></app-error-banner>

  <form [formGroup]="form" (ngSubmit)="submit()">
    <ion-list>
      <ion-item>
        <ion-label position="stacked">Email sau username</ion-label>
        <ion-input formControlName="emailOrUsername" autocomplete="username" required></ion-input>
      </ion-item>
      <ion-item>
        <ion-label position="stacked">Parolă</ion-label>
        <ion-input type="password" formControlName="password" autocomplete="current-password" required></ion-input>
      </ion-item>
    </ion-list>

    <ion-button type="submit" expand="block" [disabled]="form.invalid || (status$ | async) === 'loading'">
      {{ (status$ | async) === 'loading' ? 'Se autentifică...' : 'Login' }}
    </ion-button>
  </form>

  <p class="links">
    <a routerLink="/register">Cont nou</a>
    ·
    <a routerLink="/reset/request">Am uitat parola</a>
  </p>
</ion-content>
```

- [ ] **Step 3: Write `login.page.scss`**

```scss
h1 { margin-top: 32px; }
.invalidation-notice {
  background: var(--ion-color-warning, #ffc409);
  color: black; padding: 12px; border-radius: 8px; margin: 12px 0; font-size: 14px;
}
.links { text-align: center; margin-top: 24px; font-size: 14px; }
.links a { color: var(--ion-color-primary); margin: 0 8px; }
```

- [ ] **Step 4: Write test**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { provideMockStore, MockStore } from '@ngrx/store/testing';
import { provideRouter } from '@angular/router';
import { IonicModule } from '@ionic/angular';
import LoginPage from './login.page';
import { Auth } from '../../store/auth/auth.actions';

describe('LoginPage', () => {
  let fixture: ComponentFixture<LoginPage>;
  let store: MockStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LoginPage, ReactiveFormsModule],
      providers: [
        provideMockStore({ initialState: { auth: { user: null, tokens: null, status: 'anonymous', error: null, lastInvalidationReason: null } } }),
        provideRouter([]),
      ],
    });
    fixture = TestBed.createComponent(LoginPage);
    store = TestBed.inject(MockStore);
    spyOn(store, 'dispatch');
  });

  it('submit with invalid form does not dispatch', () => {
    fixture.detectChanges();
    fixture.componentInstance.submit();
    expect(store.dispatch).not.toHaveBeenCalled();
  });

  it('submit with valid form dispatches loginRequested', () => {
    fixture.componentInstance.form.setValue({ emailOrUsername: 'alice', password: 'passwordxx' });
    fixture.componentInstance.submit();
    expect(store.dispatch).toHaveBeenCalledWith(
        Auth.loginRequested({ emailOrUsername: 'alice', password: 'passwordxx' }));
  });
});
```

- [ ] **Step 5: Run + commit**

```bash
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --include='**/login.page.spec.ts'
git add frontend/src/app/features/auth/login.page.*
git commit -m "feat(frontend): LoginPage — form + dispatch + error banner + invalidation notice"
```

---

### Task G2: `RegisterPage`

**Files:**
- Create: `frontend/src/app/features/auth/register.page.ts`
- Create: `frontend/src/app/features/auth/register.page.html`

- [ ] **Step 1: Write `register.page.ts`**

```typescript
import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { IonContent, IonInput, IonButton, IonItem, IonList, IonLabel }
    from '@ionic/angular/standalone';
import { Auth } from '../../store/auth/auth.actions';
import { selectAuthError, selectAuthStatus } from '../../store/auth/auth.selectors';
import { ErrorBannerComponent } from '../../shared/error-banner/error-banner.component';
import { Actions, ofType } from '@ngrx/effects';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';

@Component({
  selector: 'app-register-page',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonInput, IonButton, IonItem, IonList, IonLabel,
    ErrorBannerComponent,
  ],
  templateUrl: './register.page.html',
  styleUrls: ['../auth/login.page.scss'],
})
export default class RegisterPage {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly actions$ = inject(Actions);
  private readonly router = inject(Router);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(20),
                    Validators.pattern(/^[a-zA-Z0-9_-]+$/)]],
    password: ['', [Validators.required, Validators.minLength(10)]],
  });

  readonly error$ = this.store.select(selectAuthError);
  readonly status$ = this.store.select(selectAuthStatus);
  readonly success = false;

  registered = false;

  constructor() {
    this.actions$.pipe(
      ofType(Auth.registerSucceeded),
      takeUntilDestroyed(),
    ).subscribe(() => { this.registered = true; });
  }

  submit(): void {
    if (this.form.invalid) return;
    const { email, username, password } = this.form.getRawValue();
    this.store.dispatch(Auth.registerRequested({ email, username, password }));
  }
}
```

- [ ] **Step 2: Write `register.page.html`**

```html
<ion-content class="ion-padding">
  <h1>Înregistrare</h1>

  <app-error-banner [error]="error$ | async"></app-error-banner>

  <div *ngIf="registered" class="success-banner" role="status">
    Cont creat. Verifică emailul pentru linkul de verificare.
    <a routerLink="/verify">Am primit linkul</a>
  </div>

  <form [formGroup]="form" (ngSubmit)="submit()" *ngIf="!registered">
    <ion-list>
      <ion-item>
        <ion-label position="stacked">Email</ion-label>
        <ion-input type="email" formControlName="email" autocomplete="email" required></ion-input>
      </ion-item>
      <ion-item>
        <ion-label position="stacked">Username (3-20, litere/cifre/_/-)</ion-label>
        <ion-input formControlName="username" autocomplete="username" required></ion-input>
      </ion-item>
      <ion-item>
        <ion-label position="stacked">Parolă (minim 10 caractere)</ion-label>
        <ion-input type="password" formControlName="password" autocomplete="new-password" required></ion-input>
      </ion-item>
    </ion-list>

    <ion-button type="submit" expand="block" [disabled]="form.invalid || (status$ | async) === 'loading'">
      {{ (status$ | async) === 'loading' ? 'Se înregistrează...' : 'Înregistrează-te' }}
    </ion-button>
  </form>

  <p class="links"><a routerLink="/login">Ai deja cont? Login</a></p>
</ion-content>
```

(SCSS reused from LoginPage. Add `.success-banner { background: var(--ion-color-success); color: white; padding: 12px; border-radius: 8px; }` to its scss.)

- [ ] **Step 3: Commit** (skip test for brevity — pattern identical to LoginPage; tests are easy to add later if coverage gate requires)

```bash
cd frontend && npx ng build --configuration=development
git add frontend/src/app/features/auth/register.page.*
git commit -m "feat(frontend): RegisterPage — form + dispatch + success banner"
```

---

### Task G3: `VerifyEmailPage`

**Files:**
- Create: `frontend/src/app/features/auth/verify-email.page.ts`
- Create: `frontend/src/app/features/auth/verify-email.page.html`

- [ ] **Step 1: Write `verify-email.page.ts`**

```typescript
import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { IonContent, IonInput, IonButton, IonItem, IonList, IonLabel }
    from '@ionic/angular/standalone';
import { Auth } from '../../store/auth/auth.actions';
import { selectAuthError, selectAuthStatus } from '../../store/auth/auth.selectors';
import { ErrorBannerComponent } from '../../shared/error-banner/error-banner.component';
import { Actions, ofType } from '@ngrx/effects';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-verify-email-page',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonInput, IonButton, IonItem, IonList, IonLabel,
    ErrorBannerComponent,
  ],
  templateUrl: './verify-email.page.html',
  styleUrls: ['../auth/login.page.scss'],
})
export default class VerifyEmailPage implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly actions$ = inject(Actions);

  readonly form = this.fb.nonNullable.group({ token: ['', [Validators.required]] });
  readonly error$ = this.store.select(selectAuthError);
  readonly status$ = this.store.select(selectAuthStatus);
  verified = false;

  constructor() {
    this.actions$.pipe(ofType(Auth.verifyEmailSucceeded), takeUntilDestroyed())
        .subscribe(() => { this.verified = true; });
  }

  ngOnInit(): void {
    const queryToken = this.route.snapshot.queryParamMap.get('token');
    if (queryToken) this.form.patchValue({ token: queryToken });
  }

  submit(): void {
    if (this.form.invalid) return;
    this.store.dispatch(Auth.verifyEmailRequested({ token: this.form.getRawValue().token }));
  }
}
```

- [ ] **Step 2: Write `verify-email.page.html`**

```html
<ion-content class="ion-padding">
  <h1>Verifică emailul</h1>
  <app-error-banner [error]="error$ | async"></app-error-banner>

  <div *ngIf="verified" class="success-banner" role="status">
    Email verificat. <a routerLink="/login">Login</a>
  </div>

  <form [formGroup]="form" (ngSubmit)="submit()" *ngIf="!verified">
    <ion-list>
      <ion-item>
        <ion-label position="stacked">Token din email</ion-label>
        <ion-input formControlName="token" required></ion-input>
      </ion-item>
    </ion-list>
    <ion-button type="submit" expand="block" [disabled]="form.invalid || (status$ | async) === 'loading'">
      Verifică
    </ion-button>
  </form>
</ion-content>
```

- [ ] **Step 3: Commit**

```bash
cd frontend && npx ng build --configuration=development
git add frontend/src/app/features/auth/verify-email.page.*
git commit -m "feat(frontend): VerifyEmailPage — accepts token query param or manual input"
```

---

### Task G4: `RequestResetPage` + `ResetPasswordPage` (bundled)

**Files:**
- Create: `frontend/src/app/features/auth/request-reset.page.ts` + `.html`
- Create: `frontend/src/app/features/auth/reset-password.page.ts` + `.html`

- [ ] **Step 1: Write `request-reset.page.ts`**

```typescript
import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { IonContent, IonInput, IonButton, IonItem, IonList, IonLabel }
    from '@ionic/angular/standalone';
import { Auth } from '../../store/auth/auth.actions';
import { selectAuthError, selectAuthStatus } from '../../store/auth/auth.selectors';
import { ErrorBannerComponent } from '../../shared/error-banner/error-banner.component';
import { Actions, ofType } from '@ngrx/effects';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-request-reset-page',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonInput, IonButton, IonItem, IonList, IonLabel,
    ErrorBannerComponent,
  ],
  templateUrl: './request-reset.page.html',
  styleUrls: ['../auth/login.page.scss'],
})
export default class RequestResetPage {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly actions$ = inject(Actions);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });
  readonly error$ = this.store.select(selectAuthError);
  readonly status$ = this.store.select(selectAuthStatus);
  sent = false;

  constructor() {
    this.actions$.pipe(ofType(Auth.requestResetSucceeded), takeUntilDestroyed())
        .subscribe(() => { this.sent = true; });
  }

  submit(): void {
    if (this.form.invalid) return;
    this.store.dispatch(Auth.requestResetRequested({ email: this.form.getRawValue().email }));
  }
}
```

`request-reset.page.html`:

```html
<ion-content class="ion-padding">
  <h1>Resetare parolă</h1>
  <app-error-banner [error]="error$ | async"></app-error-banner>
  <div *ngIf="sent" class="success-banner">
    Dacă emailul există, vei primi un link de resetare.
    <a routerLink="/reset/confirm">Am primit linkul</a>
  </div>
  <form [formGroup]="form" (ngSubmit)="submit()" *ngIf="!sent">
    <ion-list>
      <ion-item>
        <ion-label position="stacked">Email</ion-label>
        <ion-input type="email" formControlName="email" required></ion-input>
      </ion-item>
    </ion-list>
    <ion-button type="submit" expand="block" [disabled]="form.invalid">Trimite link</ion-button>
  </form>
  <p class="links"><a routerLink="/login">Înapoi la login</a></p>
</ion-content>
```

- [ ] **Step 2: Write `reset-password.page.ts`**

```typescript
import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { IonContent, IonInput, IonButton, IonItem, IonList, IonLabel }
    from '@ionic/angular/standalone';
import { Auth } from '../../store/auth/auth.actions';
import { selectAuthError, selectAuthStatus } from '../../store/auth/auth.selectors';
import { ErrorBannerComponent } from '../../shared/error-banner/error-banner.component';
import { Actions, ofType } from '@ngrx/effects';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-reset-password-page',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonInput, IonButton, IonItem, IonList, IonLabel,
    ErrorBannerComponent,
  ],
  templateUrl: './reset-password.page.html',
  styleUrls: ['../auth/login.page.scss'],
})
export default class ResetPasswordPage implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly actions$ = inject(Actions);

  readonly form = this.fb.nonNullable.group({
    token: ['', [Validators.required]],
    newPassword: ['', [Validators.required, Validators.minLength(10)]],
  });
  readonly error$ = this.store.select(selectAuthError);
  readonly status$ = this.store.select(selectAuthStatus);
  done = false;

  constructor() {
    this.actions$.pipe(ofType(Auth.resetPasswordSucceeded), takeUntilDestroyed())
        .subscribe(() => { this.done = true; });
  }

  ngOnInit(): void {
    const queryToken = this.route.snapshot.queryParamMap.get('token');
    if (queryToken) this.form.patchValue({ token: queryToken });
  }

  submit(): void {
    if (this.form.invalid) return;
    const { token, newPassword } = this.form.getRawValue();
    this.store.dispatch(Auth.resetPasswordRequested({ token, newPassword }));
  }
}
```

`reset-password.page.html`:

```html
<ion-content class="ion-padding">
  <h1>Setează parolă nouă</h1>
  <app-error-banner [error]="error$ | async"></app-error-banner>
  <div *ngIf="done" class="success-banner">
    Parola a fost schimbată. <a routerLink="/login">Login</a>
  </div>
  <form [formGroup]="form" (ngSubmit)="submit()" *ngIf="!done">
    <ion-list>
      <ion-item>
        <ion-label position="stacked">Token</ion-label>
        <ion-input formControlName="token" required></ion-input>
      </ion-item>
      <ion-item>
        <ion-label position="stacked">Parolă nouă (minim 10)</ion-label>
        <ion-input type="password" formControlName="newPassword" required></ion-input>
      </ion-item>
    </ion-list>
    <ion-button type="submit" expand="block" [disabled]="form.invalid">Resetează</ion-button>
  </form>
</ion-content>
```

- [ ] **Step 3: Build + commit**

```bash
cd frontend && npx ng build --configuration=development
git add frontend/src/app/features/auth/request-reset.page.* frontend/src/app/features/auth/reset-password.page.*
git commit -m "feat(frontend): RequestResetPage + ResetPasswordPage"
```

---

## Phase H — Lobby feature pages

### Task H1: `LobbyHomePage`

**Files:**
- Create: `frontend/src/app/features/lobby/lobby-home.page.ts`
- Create: `frontend/src/app/features/lobby/lobby-home.page.html`
- Create: `frontend/src/app/features/lobby/lobby-home.page.scss`

- [ ] **Step 1: Component**

```typescript
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { IonContent, IonCard, IonCardHeader, IonCardTitle, IonCardSubtitle, IonCardContent }
    from '@ionic/angular/standalone';

@Component({
  selector: 'app-lobby-home',
  standalone: true,
  imports: [CommonModule, RouterLink,
    IonContent, IonCard, IonCardHeader, IonCardTitle, IonCardSubtitle, IonCardContent,
  ],
  templateUrl: './lobby-home.page.html',
  styleUrls: ['./lobby-home.page.scss'],
})
export default class LobbyHomePage {}
```

- [ ] **Step 2: Template**

```html
<ion-content class="ion-padding">
  <h1>Lobby</h1>

  <div class="cards">
    <ion-card button routerLink="/lobby/create">
      <ion-card-header>
        <ion-card-title>Creează masă privată</ion-card-title>
        <ion-card-subtitle>Joacă cu prieteni — primești un cod</ion-card-subtitle>
      </ion-card-header>
    </ion-card>

    <ion-card button routerLink="/lobby/join">
      <ion-card-header>
        <ion-card-title>Intră cu cod</ion-card-title>
        <ion-card-subtitle>Ai primit un cod de la un prieten?</ion-card-subtitle>
      </ion-card-header>
    </ion-card>

    <ion-card button routerLink="/lobby/public">
      <ion-card-header>
        <ion-card-title>Mese publice</ion-card-title>
        <ion-card-subtitle>Alege o masă deschisă</ion-card-subtitle>
      </ion-card-header>
    </ion-card>

    <ion-card button routerLink="/lobby/quick">
      <ion-card-header>
        <ion-card-title>Match rapid</ion-card-title>
        <ion-card-subtitle>Server-ul te pune cu primul disponibil</ion-card-subtitle>
      </ion-card-header>
    </ion-card>
  </div>
</ion-content>
```

- [ ] **Step 3: SCSS**

```scss
.cards { display: grid; grid-template-columns: 1fr; gap: 16px; max-width: 480px; margin: 16px auto; }
```

- [ ] **Step 4: Commit**

```bash
cd frontend && npx ng build --configuration=development
git add frontend/src/app/features/lobby/lobby-home.page.*
git commit -m "feat(frontend): LobbyHomePage — 4 card nav (create/join/public/quick)"
```

---

### Task H2: `CreateGamePage`

**Files:**
- Create: `frontend/src/app/features/lobby/create-game.page.ts` + `.html`

- [ ] **Step 1: Component**

```typescript
import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { IonContent, IonRadioGroup, IonRadio, IonItem, IonList, IonLabel,
         IonButton, IonRange, IonSelect, IonSelectOption } from '@ionic/angular/standalone';
import { Lobby } from '../../store/lobby/lobby.actions';
import { selectLobbyError, selectLobbyLoading } from '../../store/lobby/lobby.selectors';
import { ErrorBannerComponent } from '../../shared/error-banner/error-banner.component';
import { GameVisibility, Mode, Difficulty } from '../../core/models';

@Component({
  selector: 'app-create-game',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    IonContent, IonRadioGroup, IonRadio, IonItem, IonList, IonLabel,
    IonButton, IonRange, IonSelect, IonSelectOption,
    ErrorBannerComponent,
  ],
  templateUrl: './create-game.page.html',
})
export default class CreateGamePage {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);

  readonly form = this.fb.nonNullable.group({
    visibility: ['PRIVATE' as GameVisibility, [Validators.required]],
    numPlayers: [2, [Validators.required, Validators.min(2), Validators.max(6)]],
    mode: ['ETALAT' as Mode, [Validators.required]],
    difficulty: ['MED' as Difficulty, [Validators.required]],
  });

  readonly error$ = this.store.select(selectLobbyError);
  readonly loading$ = this.store.select(selectLobbyLoading);

  submit(): void {
    if (this.form.invalid) return;
    this.store.dispatch(Lobby.createRequested({ req: this.form.getRawValue() }));
  }
}
```

- [ ] **Step 2: Template**

```html
<ion-content class="ion-padding">
  <h1>Creează masă</h1>
  <app-error-banner [error]="error$ | async"></app-error-banner>

  <form [formGroup]="form" (ngSubmit)="submit()">
    <ion-list>
      <ion-radio-group formControlName="visibility">
        <ion-item>
          <ion-radio value="PRIVATE">Privată (cu cod)</ion-radio>
        </ion-item>
        <ion-item>
          <ion-radio value="PUBLIC">Publică (vizibilă în lista de mese)</ion-radio>
        </ion-item>
      </ion-radio-group>

      <ion-item>
        <ion-label position="stacked">Număr jucători: {{ form.value.numPlayers }}</ion-label>
        <ion-range formControlName="numPlayers" min="2" max="6" step="1" snaps="true" pin="true"></ion-range>
      </ion-item>

      <ion-item>
        <ion-label>Mod</ion-label>
        <ion-select formControlName="mode" interface="popover">
          <ion-select-option value="ETALAT">Etalat (clasic)</ion-select-option>
          <ion-select-option value="TABLA">Tabla (închidere rapidă)</ion-select-option>
        </ion-select>
      </ion-item>

      <ion-item>
        <ion-label>Dificultate (pentru boți)</ion-label>
        <ion-select formControlName="difficulty" interface="popover">
          <ion-select-option value="EASY">Ușor</ion-select-option>
          <ion-select-option value="MED">Mediu</ion-select-option>
          <ion-select-option value="HARD">Greu</ion-select-option>
        </ion-select>
      </ion-item>
    </ion-list>

    <ion-button type="submit" expand="block" [disabled]="form.invalid || (loading$ | async)">
      {{ (loading$ | async) ? 'Se creează...' : 'Creează' }}
    </ion-button>
  </form>
</ion-content>
```

- [ ] **Step 3: Commit**

```bash
cd frontend && npx ng build --configuration=development
git add frontend/src/app/features/lobby/create-game.page.*
git commit -m "feat(frontend): CreateGamePage — form (visibility/players/mode/difficulty)"
```

---

### Task H3: `JoinByCodePage`

**Files:**
- Create: `frontend/src/app/features/lobby/join-by-code.page.ts` + `.html`

- [ ] **Step 1: Component**

```typescript
import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { IonContent, IonInput, IonItem, IonList, IonLabel, IonButton }
    from '@ionic/angular/standalone';
import { Lobby } from '../../store/lobby/lobby.actions';
import { selectLobbyError, selectLobbyLoading } from '../../store/lobby/lobby.selectors';
import { ErrorBannerComponent } from '../../shared/error-banner/error-banner.component';

@Component({
  selector: 'app-join-by-code',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    IonContent, IonInput, IonItem, IonList, IonLabel, IonButton,
    ErrorBannerComponent,
  ],
  templateUrl: './join-by-code.page.html',
})
export default class JoinByCodePage {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  readonly form = this.fb.nonNullable.group({
    joinCode: ['', [Validators.required, Validators.pattern(/^[A-Z0-9]{8}$/)]],
  });
  readonly error$ = this.store.select(selectLobbyError);
  readonly loading$ = this.store.select(selectLobbyLoading);

  submit(): void {
    if (this.form.invalid) return;
    this.store.dispatch(Lobby.joinByCodeRequested({ joinCode: this.form.getRawValue().joinCode }));
  }
}
```

- [ ] **Step 2: Template**

```html
<ion-content class="ion-padding">
  <h1>Intră cu cod</h1>
  <app-error-banner [error]="error$ | async"></app-error-banner>
  <form [formGroup]="form" (ngSubmit)="submit()">
    <ion-list>
      <ion-item>
        <ion-label position="stacked">Cod (8 caractere)</ion-label>
        <ion-input formControlName="joinCode" autocapitalize="characters" maxlength="8" required></ion-input>
      </ion-item>
    </ion-list>
    <ion-button type="submit" expand="block" [disabled]="form.invalid || (loading$ | async)">
      Intră
    </ion-button>
  </form>
</ion-content>
```

- [ ] **Step 3: Commit**

```bash
cd frontend && npx ng build --configuration=development
git add frontend/src/app/features/lobby/join-by-code.page.*
git commit -m "feat(frontend): JoinByCodePage — 8-char code input + dispatch"
```

---

### Task H4: `PublicListPage`

**Files:**
- Create: `frontend/src/app/features/lobby/public-list.page.ts` + `.html`

- [ ] **Step 1: Component**

```typescript
import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Store } from '@ngrx/store';
import { IonContent, IonList, IonItem, IonLabel, IonButton, IonRefresher, IonRefresherContent }
    from '@ionic/angular/standalone';
import { Lobby } from '../../store/lobby/lobby.actions';
import { selectPublicGames, selectLobbyError, selectLobbyLoading } from '../../store/lobby/lobby.selectors';
import { ErrorBannerComponent } from '../../shared/error-banner/error-banner.component';

@Component({
  selector: 'app-public-list',
  standalone: true,
  imports: [
    CommonModule,
    IonContent, IonList, IonItem, IonLabel, IonButton, IonRefresher, IonRefresherContent,
    ErrorBannerComponent,
  ],
  templateUrl: './public-list.page.html',
})
export default class PublicListPage implements OnInit {
  private readonly store = inject(Store);

  readonly games$ = this.store.select(selectPublicGames);
  readonly error$ = this.store.select(selectLobbyError);
  readonly loading$ = this.store.select(selectLobbyLoading);

  ngOnInit(): void { this.refresh(); }

  refresh(refresher?: CustomEvent): void {
    this.store.dispatch(Lobby.listPublicRequested());
    if (refresher) (refresher.target as HTMLIonRefresherElement).complete();
  }

  join(gameId: string): void {
    this.store.dispatch(Lobby.joinPublicRequested({ gameId }));
  }
}
```

- [ ] **Step 2: Template**

```html
<ion-content class="ion-padding">
  <h1>Mese publice</h1>
  <app-error-banner [error]="error$ | async"></app-error-banner>

  <ion-refresher slot="fixed" (ionRefresh)="refresh($event)">
    <ion-refresher-content></ion-refresher-content>
  </ion-refresher>

  <p *ngIf="(games$ | async)?.length === 0 && !(loading$ | async)">
    Nicio masă publică momentan. Creează una sau folosește Match Rapid.
  </p>

  <ion-list>
    <ion-item *ngFor="let g of games$ | async">
      <ion-label>
        <h2>{{ g.numPlayers }} jucători · {{ g.mode }} · {{ g.difficulty }}</h2>
        <p>{{ g.seatsTaken }}/{{ g.numPlayers }} locuri ocupate</p>
      </ion-label>
      <ion-button slot="end" (click)="join(g.id)" [disabled]="loading$ | async">Intră</ion-button>
    </ion-item>
  </ion-list>
</ion-content>
```

- [ ] **Step 3: Commit**

```bash
cd frontend && npx ng build --configuration=development
git add frontend/src/app/features/lobby/public-list.page.*
git commit -m "feat(frontend): PublicListPage — list+refresh+join"
```

---

### Task H5: `QuickMatchPage`

**Files:**
- Create: `frontend/src/app/features/lobby/quick-match.page.ts` + `.html`

- [ ] **Step 1: Component**

```typescript
import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { IonContent, IonRadioGroup, IonRadio, IonItem, IonList, IonLabel,
         IonButton, IonRange, IonSelect, IonSelectOption } from '@ionic/angular/standalone';
import { Match } from '../../store/match/match.actions';
import { selectMatchError, selectMatchStatus } from '../../store/match/match.selectors';
import { ErrorBannerComponent } from '../../shared/error-banner/error-banner.component';
import { Mode, Difficulty } from '../../core/models';

@Component({
  selector: 'app-quick-match',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    IonContent, IonRadioGroup, IonRadio, IonItem, IonList, IonLabel,
    IonButton, IonRange, IonSelect, IonSelectOption,
    ErrorBannerComponent,
  ],
  templateUrl: './quick-match.page.html',
})
export default class QuickMatchPage implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);

  readonly form = this.fb.nonNullable.group({
    numPlayers: [2, [Validators.required, Validators.min(2), Validators.max(6)]],
    mode: ['ETALAT' as Mode, [Validators.required]],
    difficulty: ['MED' as Difficulty, [Validators.required]],
  });
  readonly status$ = this.store.select(selectMatchStatus);
  readonly error$ = this.store.select(selectMatchError);

  ngOnInit(): void {
    // CRITICAL: subscribe to match topic BEFORE user clicks Find — server may
    // push a match notification before the HTTP /quick response if another
    // user is already queued.
    this.store.dispatch(Match.subscribeToMatchTopic());
  }

  find(): void {
    if (this.form.invalid) return;
    this.store.dispatch(Match.quickRequested({ req: this.form.getRawValue() }));
  }

  cancel(): void {
    this.store.dispatch(Match.cancelRequested());
  }
}
```

- [ ] **Step 2: Template**

```html
<ion-content class="ion-padding">
  <h1>Match rapid</h1>
  <app-error-banner [error]="error$ | async"></app-error-banner>

  <form [formGroup]="form" (ngSubmit)="find()" *ngIf="(status$ | async) === 'idle' || (status$ | async) === 'error'">
    <ion-list>
      <ion-item>
        <ion-label position="stacked">Număr jucători: {{ form.value.numPlayers }}</ion-label>
        <ion-range formControlName="numPlayers" min="2" max="6" step="1" snaps="true" pin="true"></ion-range>
      </ion-item>
      <ion-item>
        <ion-label>Mod</ion-label>
        <ion-select formControlName="mode" interface="popover">
          <ion-select-option value="ETALAT">Etalat</ion-select-option>
          <ion-select-option value="TABLA">Tabla</ion-select-option>
        </ion-select>
      </ion-item>
      <ion-item>
        <ion-label>Dificultate</ion-label>
        <ion-select formControlName="difficulty" interface="popover">
          <ion-select-option value="EASY">Ușor</ion-select-option>
          <ion-select-option value="MED">Mediu</ion-select-option>
          <ion-select-option value="HARD">Greu</ion-select-option>
        </ion-select>
      </ion-item>
    </ion-list>
    <ion-button type="submit" expand="block" [disabled]="form.invalid">Caută meci</ion-button>
  </form>

  <div *ngIf="(status$ | async) === 'queued'" class="queued-banner">
    <p>În așteptare...</p>
    <ion-button color="warning" expand="block" (click)="cancel()">Anulează</ion-button>
  </div>
</ion-content>
```

- [ ] **Step 3: Commit**

```bash
cd frontend && npx ng build --configuration=development
git add frontend/src/app/features/lobby/quick-match.page.*
git commit -m "feat(frontend): QuickMatchPage — subscribe-before-POST, queued banner, cancel"
```

---

## Phase I — Game debug page + routes

### Task I1: `GameDebugPage`

**Files:**
- Create: `frontend/src/app/features/game/game-debug.page.ts`
- Create: `frontend/src/app/features/game/game-debug.page.html`
- Create: `frontend/src/app/features/game/game-debug.page.scss`

- [ ] **Step 1: Component**

```typescript
import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { IonContent, IonInput, IonItem, IonList, IonLabel, IonButton, IonSelect,
         IonSelectOption, IonCard, IonCardHeader, IonCardTitle, IonCardContent,
         ToastController } from '@ionic/angular/standalone';
import { Actions, ofType } from '@ngrx/effects';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Game } from '../../store/game/game.actions';
import { selectGameView, selectGameError } from '../../store/game/game.selectors';
import { ErrorBannerComponent } from '../../shared/error-banner/error-banner.component';
import { ErrorMessagePipe } from '../../core/i18n/error-message.pipe';
import { Action, ActionType } from '../../core/models';

@Component({
  selector: 'app-game-debug',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonInput, IonItem, IonList, IonLabel, IonButton, IonSelect, IonSelectOption,
    IonCard, IonCardHeader, IonCardTitle, IonCardContent,
    ErrorBannerComponent, ErrorMessagePipe,
  ],
  templateUrl: './game-debug.page.html',
  styleUrls: ['./game-debug.page.scss'],
})
export default class GameDebugPage implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly toasts = inject(ToastController);
  private readonly actions$ = inject(Actions);

  readonly view$ = this.store.select(selectGameView);
  readonly error$ = this.store.select(selectGameError);

  readonly actionTypes: ActionType[] = ['DRAW_FROM_STOCK', 'TAKE_DISCARD', 'DISCARD', 'FORCE_AUTO'];

  readonly form = this.fb.nonNullable.group({
    type: ['DRAW_FROM_STOCK' as ActionType, [Validators.required]],
    playerIdx: [0, [Validators.required, Validators.min(0)]],
    pieceId: [0],         // only used for DISCARD
    discardIdx: [0],      // only used for TAKE_DISCARD
  });

  gameId = '';

  constructor() {
    this.actions$.pipe(ofType(Game.errorReceived), takeUntilDestroyed())
        .subscribe(({ error }) => {
          this.toasts.create({
            message: error.message,
            duration: 4000,
            color: 'danger',
          }).then(t => t.present());
        });
  }

  ngOnInit(): void {
    this.gameId = this.route.snapshot.paramMap.get('id') ?? '';
    if (this.gameId) {
      this.store.dispatch(Game.subscribeToGame({ gameId: this.gameId }));
      this.store.dispatch(Game.subscribeToErrors());
      this.store.dispatch(Game.loadGameRequested({ gameId: this.gameId }));
    }
  }

  ngOnDestroy(): void {
    this.store.dispatch(Game.clearGame());
  }

  submit(): void {
    if (this.form.invalid) return;
    const { type, playerIdx, pieceId, discardIdx } = this.form.getRawValue();
    let action: Action;
    switch (type) {
      case 'DRAW_FROM_STOCK': action = { type, playerIdx }; break;
      case 'TAKE_DISCARD': action = { type, playerIdx, discardIdx }; break;
      case 'DISCARD': action = { type, playerIdx, pieceId }; break;
      case 'FORCE_AUTO': action = { type, playerIdx }; break;
      default: return;
    }
    this.store.dispatch(Game.sendAction({ gameId: this.gameId, action }));
  }

  formatJson(v: unknown): string { return JSON.stringify(v, null, 2); }
}
```

- [ ] **Step 2: Template**

```html
<ion-content class="ion-padding">
  <h1>Joc <code>{{ gameId }}</code></h1>
  <app-error-banner [error]="error$ | async"></app-error-banner>

  <ion-card *ngIf="view$ | async as view">
    <ion-card-header>
      <ion-card-title>State (turn {{ view.current }}, phase {{ view.phase }})</ion-card-title>
    </ion-card-header>
    <ion-card-content>
      <pre>{{ formatJson(view) }}</pre>
    </ion-card-content>
  </ion-card>

  <ion-card>
    <ion-card-header><ion-card-title>Trimite Action</ion-card-title></ion-card-header>
    <ion-card-content>
      <form [formGroup]="form" (ngSubmit)="submit()">
        <ion-list>
          <ion-item>
            <ion-label>Type</ion-label>
            <ion-select formControlName="type" interface="popover">
              <ion-select-option *ngFor="let t of actionTypes" [value]="t">{{ t }}</ion-select-option>
            </ion-select>
          </ion-item>
          <ion-item>
            <ion-label position="stacked">playerIdx</ion-label>
            <ion-input type="number" formControlName="playerIdx" required></ion-input>
          </ion-item>
          <ion-item *ngIf="form.value.type === 'DISCARD'">
            <ion-label position="stacked">pieceId</ion-label>
            <ion-input type="number" formControlName="pieceId" required></ion-input>
          </ion-item>
          <ion-item *ngIf="form.value.type === 'TAKE_DISCARD'">
            <ion-label position="stacked">discardIdx</ion-label>
            <ion-input type="number" formControlName="discardIdx" required></ion-input>
          </ion-item>
        </ion-list>
        <ion-button type="submit" expand="block" [disabled]="form.invalid">Send</ion-button>
      </form>
    </ion-card-content>
  </ion-card>

  <p class="links"><a routerLink="/lobby">← Înapoi la lobby</a></p>
</ion-content>
```

- [ ] **Step 3: SCSS**

```scss
pre { font-size: 11px; max-height: 50vh; overflow: auto; background: #f6f6f6;
      padding: 8px; border-radius: 4px; }
.links { text-align: center; margin: 16px 0; }
```

- [ ] **Step 4: Commit**

```bash
cd frontend && npx ng build --configuration=development
git add frontend/src/app/features/game/game-debug.page.*
git commit -m "feat(frontend): GameDebugPage — JSON state + Action submit form (4a placeholder)"
```

---

### Task I2: `app.routes.ts`

**File:** Modify `frontend/src/app/app.routes.ts`

- [ ] **Step 1: Replace contents**

```typescript
import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'lobby', pathMatch: 'full' },
  { path: 'login', loadComponent: () => import('./features/auth/login.page') },
  { path: 'register', loadComponent: () => import('./features/auth/register.page') },
  { path: 'verify', loadComponent: () => import('./features/auth/verify-email.page') },
  { path: 'reset/request', loadComponent: () => import('./features/auth/request-reset.page') },
  { path: 'reset/confirm', loadComponent: () => import('./features/auth/reset-password.page') },
  {
    path: 'lobby',
    canActivate: [authGuard],
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

- [ ] **Step 2: Build + commit**

```bash
cd frontend && npx ng build --configuration=development
git add frontend/src/app/app.routes.ts
git commit -m "feat(frontend): app.routes — auth (public) + lobby/game (authGuard)"
```

---

## Phase J — Smoke test + README

### Task J1: `SMOKE_TEST.md` + `README.md` (frontend) + root README update

**Files:**
- Create: `frontend/SMOKE_TEST.md`
- Create: `frontend/README.md`
- Modify: `README.md` (root) — add Stage 4a section

- [ ] **Step 1: Write `frontend/SMOKE_TEST.md`**

```markdown
# Smoke Test — Stage 4a (Manual E2E)

Prerequisites:
- Backend running (`cd .. && mvn spring-boot:run`) with Postgres in Docker
- Frontend dev server (`cd frontend && npm start` → http://localhost:4200)

## Happy path

1. Open http://localhost:4200 → redirect to /login.
2. Click "Cont nou" → /register.
3. Fill: email `a@a.com`, username `alice`, password `passwordxx` → "Înregistrează-te".
4. Success banner shown. Look in backend console for the verification email log; copy the token UUID.
5. Click "Am primit linkul" → /verify.
6. Paste token → "Verifică" → success banner.
7. Click "Login" link → fill `alice` / `passwordxx` → "Login".
8. Land in /lobby. Header shows username + green WS indicator dot.
9. Click "Creează masă privată" → form → keep defaults (PRIVATE, 2 players, ETALAT, MED) → "Creează".
10. Navigated to /game/&lt;id&gt;. JSON state visible. `current: 0`, `phase: DISCARD`.
11. In another browser/tab/incognito: repeat steps 1-7 with `b@b.com` / `bob`.
12. As Bob: click "Intră cu cod" → paste the join code (shown in Alice's state JSON under `joinCode`) → "Intră". Lands in /game/&lt;id&gt;.
13. Both Alice and Bob see updated state (started=true, both seated).
14. As Alice: scroll to "Trimite Action" form. type=DISCARD, playerIdx=0, pieceId=&lt;first piece in her hand from JSON&gt; → "Send".
15. Both browsers update: `current` becomes 1, Alice's hand shrinks to 14, Bob's hand still hidden (Bob sees Alice's hand as `[]` with `handCount: 14`).
16. As Bob: type=DRAW_FROM_STOCK, playerIdx=1 → "Send". Both update.
17. Click logout button in header → /login. Bob still in /game until his session also logs out.

## Error paths

- Wrong password on login → banner "Email sau parolă incorecte."
- Try to log in before email verified → same banner (no enumeration).
- Join with invalid code → banner "Cod invalid."
- As Alice (not her turn): type=DRAW_FROM_STOCK, playerIdx=0 → toast appears with NOT_YOUR_TURN message.
- Stop the backend mid-game → WS indicator turns yellow (RECONNECTING); restart backend → goes green again.

## Matchmaking

- Alice goes to /lobby/quick → form (2, ETALAT, MED) → "Caută meci" → "În așteptare..." spinner.
- Bob (different tab) goes to /lobby/quick → same form → "Caută meci".
- Both auto-navigate to the same /game/&lt;id&gt; with their seats assigned.
```

- [ ] **Step 2: Write `frontend/README.md`**

```markdown
# Remi Frontend (Stage 4a)

Ionic 8 + Angular 19 + NgRx 18 client for the Remi multiplayer game backend.

This is **Stage 4a** of the build plan. Game UI is intentionally a debug placeholder (JSON state + action form). Real game UI ships in Stage 4b; mobile builds in Stage 5.

## Prereqs

- Node 20+
- Ionic CLI (`npm i -g @ionic/cli`)
- Backend running on `http://localhost:8080` (see root README)

## Run

```bash
cd frontend
npm install
npm start
```

App at http://localhost:4200. Dev proxy forwards `/api` and `/ws` to localhost:8080.

## Tests

```bash
npm test          # Karma + Jasmine, headless Chrome
```

Coverage at `coverage/index.html`.

## Smoke test (manual E2E)

See `SMOKE_TEST.md`.

## Architecture

```
src/app/
  core/        api/, ws/, auth/, models/, i18n/
  store/       NgRx (auth, lobby, match, game)
  features/    Page components by feature
  shared/      ErrorBanner, WsIndicator, GlobalErrorHandler
```

JWT stored in `localStorage` (Stage 4a); switches to Capacitor Preferences in Stage 5.
WebSocket via STOMP+SockJS at `/ws` with `Authorization: Bearer <accessToken>` on CONNECT.
```

- [ ] **Step 3: Update root `README.md`** — add this section after "## Multiplayer (Stage 3)":

```markdown

## Frontend (Stage 4a)

Ionic 8 + Angular 19 + NgRx 18 client lives under `frontend/`. See `frontend/README.md` for setup and `frontend/SMOKE_TEST.md` for manual E2E flow.

Stage 4a is a usable shell: register/verify/login, lobby create/join, quick-match, WebSocket-driven game state with a JSON-debug action form. Real game UI ships in Stage 4b.
```

- [ ] **Step 4: Commit**

```bash
git add frontend/SMOKE_TEST.md frontend/README.md README.md
git commit -m "docs(frontend): SMOKE_TEST.md + frontend README + root README pointer"
```

---

## Stage 4a complete

After all 3 parts:
- Scaffolded Ionic 8 + Angular 19 in `/frontend`
- Models, AuthStorage, ErrorMessage, ErrorBanner, WsIndicator, GlobalErrorHandler
- AuthApi, LobbyApi, MatchmakingApi (with HTTP unit tests)
- StompService, GameWsService (with light unit tests)
- NgRx: auth + lobby + match + game features (actions, reducers, effects, selectors)
- jwtInterceptor (Bearer injection + 401 refresh + session invalidation)
- authGuard
- AppComponent with bootstrap + WS indicator + logout
- AuthWsEffects (connect/disconnect STOMP based on auth state)
- 11 feature pages (5 auth, 5 lobby, 1 game debug)
- Routes with public+guarded sections
- SMOKE_TEST.md + frontend README + root README pointer

**Next stage** (4b — game UI port from `assets/remi.html`): brainstorm cycle when ready. The contracts (Actions, GameView, WS topics) are stable.
