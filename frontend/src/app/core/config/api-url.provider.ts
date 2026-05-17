import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { Capacitor } from '@capacitor/core';
import { API_URL, WS_URL } from './api-url.tokens';

export function resolveApiUrl(): string {
  switch (Capacitor.getPlatform()) {
    case 'ios': return 'http://localhost:8080/api';
    case 'android': return 'http://10.0.2.2:8080/api';
    default: return '/api';
  }
}

export function resolveWsUrl(): string {
  switch (Capacitor.getPlatform()) {
    case 'ios': return 'http://localhost:8080/ws';
    case 'android': return 'http://10.0.2.2:8080/ws';
    default: return '/ws';
  }
}

export function provideApiUrls(): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: API_URL, useFactory: resolveApiUrl },
    { provide: WS_URL, useFactory: resolveWsUrl },
  ]);
}
