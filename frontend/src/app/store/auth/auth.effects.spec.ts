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
