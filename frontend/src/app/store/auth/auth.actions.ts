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
