import { TestBed } from '@angular/core/testing';
import { AuthStorageService } from './auth-storage.service';
import { SecureStorageService } from '../storage/secure-storage.service';
import { AuthTokens } from '../models';

describe('AuthStorageService', () => {
  let service: AuthStorageService;
  let secure: jasmine.SpyObj<SecureStorageService>;
  const TOKENS: AuthTokens = {
    accessToken: 'access-1',
    refreshToken: 'refresh-1',
    accessExpiresAt: '2026-12-31T00:00:00Z',
  };

  beforeEach(() => {
    localStorage.clear();
    secure = jasmine.createSpyObj<SecureStorageService>('SecureStorageService',
      ['set', 'get', 'remove']);
    secure.set.and.resolveTo();
    secure.remove.and.resolveTo();
    secure.get.and.resolveTo(null);
    TestBed.configureTestingModule({
      providers: [{ provide: SecureStorageService, useValue: secure }],
    });
    service = TestBed.inject(AuthStorageService);
  });

  it('returns null when nothing stored', () => {
    expect(service.getTokens()).toBeNull();
  });

  it('stores and reads back tokens', () => {
    service.setTokens(TOKENS);
    expect(service.getTokens()).toEqual(TOKENS);
  });

  it('mirrors writes into the secure store', () => {
    service.setTokens(TOKENS);
    expect(secure.set).toHaveBeenCalledWith('remi.auth.tokens', JSON.stringify(TOKENS));
  });

  it('clear removes stored tokens from both stores', () => {
    service.setTokens(TOKENS);
    service.clear();
    expect(service.getTokens()).toBeNull();
    expect(secure.remove).toHaveBeenCalledWith('remi.auth.tokens');
  });

  it('returns null when stored JSON is malformed', () => {
    localStorage.setItem('remi.auth.tokens', '{not-json');
    expect(service.getTokens()).toBeNull();
  });

  it('migrateLegacyToken hydrates localStorage from secure when only secure has data', async () => {
    secure.get.and.resolveTo(JSON.stringify(TOKENS));
    await service.migrateLegacyToken();
    expect(service.getTokens()).toEqual(TOKENS);
  });

  it('migrateLegacyToken pushes localStorage into secure when only localStorage has data', async () => {
    service.setTokens(TOKENS);
    secure.set.calls.reset();
    secure.get.and.resolveTo(null);
    await service.migrateLegacyToken();
    expect(secure.set).toHaveBeenCalledWith('remi.auth.tokens', JSON.stringify(TOKENS));
  });
});
