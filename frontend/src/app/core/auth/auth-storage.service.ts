import { Injectable } from '@angular/core';
import { AuthTokens } from '../models';

const STORAGE_KEY = 'remi.auth.tokens';

@Injectable({ providedIn: 'root' })
export class AuthStorageService {
  setTokens(tokens: AuthTokens): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens));
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
  }
}
