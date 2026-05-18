import { Injectable, InjectionToken, inject } from '@angular/core';
import { Capacitor } from '@capacitor/core';
import { PushNotifications, Token } from '@capacitor/push-notifications';
import { DeviceTokenApi } from './device-token.api';

/**
 * Minimal facade over the subset of @capacitor/push-notifications used by the
 * service. Exposed as a DI token so tests can replace it without fighting the
 * Capacitor Proxy (which intercepts every property access on the real plugin
 * and bypasses Jasmine spies / property assignment).
 */
export interface PushNotificationsAdapter {
  requestPermissions(): Promise<{ receive: 'granted' | 'denied' | 'prompt' | 'prompt-with-rationale' }>;
  register(): Promise<void>;
  addListener(
    event: 'registration',
    cb: (t: Token) => void | Promise<void>,
  ): Promise<{ remove: () => Promise<void> }>;
}

export const PUSH_NOTIFICATIONS_ADAPTER = new InjectionToken<PushNotificationsAdapter>(
  'PUSH_NOTIFICATIONS_ADAPTER',
  {
    providedIn: 'root',
    factory: () => ({
      requestPermissions: () => PushNotifications.requestPermissions(),
      register: () => PushNotifications.register(),
      addListener: (event, cb) => PushNotifications.addListener(event, cb),
    }),
  },
);

@Injectable({ providedIn: 'root' })
export class PushNotificationsService {
  private readonly api = inject(DeviceTokenApi);
  private readonly plugin = inject(PUSH_NOTIFICATIONS_ADAPTER);

  async ensurePermission(): Promise<void> {
    const platform = Capacitor.getPlatform();
    if (platform === 'web') return;
    const { receive } = await this.plugin.requestPermissions();
    if (receive !== 'granted') return;
    await this.plugin.register();
    await this.plugin.addListener('registration', async (t: Token) => {
      await this.api.sendDeviceToken(t.value, platform as 'ios' | 'android');
    });
  }
}
