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
