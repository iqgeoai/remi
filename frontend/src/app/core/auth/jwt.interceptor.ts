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
