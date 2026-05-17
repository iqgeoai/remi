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
