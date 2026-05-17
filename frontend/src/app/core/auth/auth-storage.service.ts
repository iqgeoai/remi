import { Injectable, inject } from '@angular/core';
import { AuthTokens } from '../models';
import { SecureStorageService } from '../storage/secure-storage.service';

const STORAGE_KEY = 'remi.auth.tokens';

/**
 * Persists auth tokens.
 *
 * Native platforms benefit from Capacitor `Preferences` (Keychain on iOS,
 * SharedPreferences on Android) for at-rest durability. However the JWT
 * interceptor and bootstrap effect both need synchronous reads. To keep
 * those code paths simple we **mirror** tokens into `localStorage` (for
 * sync access) and into `SecureStorageService` (for native persistence).
 *
 * The localStorage mirror can be removed once the interceptor and the
 * bootstrap effect both become async-aware (tracked in stage 5c+).
 */
@Injectable({ providedIn: 'root' })
export class AuthStorageService {
  private readonly secure = inject(SecureStorageService);

  setTokens(tokens: AuthTokens): void {
    const serialised = JSON.stringify(tokens);
    localStorage.setItem(STORAGE_KEY, serialised);
    // fire-and-forget; native store is the durable mirror
    void this.secure.set(STORAGE_KEY, serialised);
  }

  getTokens(): AuthTokens | null {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw === null) return null;
    try {
      return JSON.parse(raw) as AuthTokens;
    } catch {
      return null;
    }
  }

  clear(): void {
    localStorage.removeItem(STORAGE_KEY);
    void this.secure.remove(STORAGE_KEY);
  }

  /**
   * One-shot reconciliation between localStorage and the native secure store.
   * - If localStorage is empty but the secure store has a value (fresh app
   *   install on native after upgrade from web-only build, or after the OS
   *   wipes WebView localStorage), hydrate localStorage from secure.
   * - If localStorage already has a value, push it into secure to keep
   *   the native mirror up to date.
   *
   * Safe to call from `AppComponent.ngOnInit` before bootstrap.
   */
  async migrateLegacyToken(): Promise<void> {
    const local = localStorage.getItem(STORAGE_KEY);
    const native = await this.secure.get(STORAGE_KEY);
    if (local === null && native !== null) {
      localStorage.setItem(STORAGE_KEY, native);
    } else if (local !== null && native === null) {
      await this.secure.set(STORAGE_KEY, local);
    }
  }
}
