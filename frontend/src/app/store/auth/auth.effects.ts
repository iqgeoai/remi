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
