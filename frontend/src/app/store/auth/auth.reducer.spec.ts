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
