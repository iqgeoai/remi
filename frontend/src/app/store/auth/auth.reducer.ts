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
  on(Auth.bootstrapFailed, () => ({ ...initialState })),

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
