import { TestBed } from '@angular/core/testing';
import { Preferences } from '@capacitor/preferences';
import { SecureStorageService } from './secure-storage.service';

// NOTE: Capacitor wraps plugins in a Proxy that bypasses Jasmine spies on
// property access. Instead, we exercise the real PreferencesWeb implementation
// (which is backed by window.localStorage with a `CapacitorStorage.` prefix)
// and assert behaviour end-to-end.
const PREF_PREFIX = 'CapacitorStorage.';

describe('SecureStorageService', () => {
  let svc: SecureStorageService;

  beforeEach(async () => {
    // Clean Preferences storage for both keys we touch.
    await Preferences.remove({ key: 'jwt' });
    TestBed.configureTestingModule({});
    svc = TestBed.inject(SecureStorageService);
  });

  afterEach(async () => {
    await Preferences.remove({ key: 'jwt' });
  });

  it('set() persists the value via Preferences', async () => {
    await svc.set('jwt', 'abc');
    expect(localStorage.getItem(`${PREF_PREFIX}jwt`)).toBe('abc');
  });

  it('get() returns the stored value', async () => {
    await svc.set('jwt', 'abc');
    expect(await svc.get('jwt')).toBe('abc');
  });

  it('get() returns null on missing key', async () => {
    expect(await svc.get('jwt')).toBeNull();
  });

  it('remove() deletes the stored value', async () => {
    await svc.set('jwt', 'abc');
    await svc.remove('jwt');
    expect(await svc.get('jwt')).toBeNull();
  });
});
