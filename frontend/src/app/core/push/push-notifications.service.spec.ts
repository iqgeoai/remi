import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Capacitor } from '@capacitor/core';
import { Token } from '@capacitor/push-notifications';
import {
  PUSH_NOTIFICATIONS_ADAPTER,
  PushNotificationsAdapter,
  PushNotificationsService,
} from './push-notifications.service';
import { DeviceTokenApi } from './device-token.api';
import { API_URL } from '../config/api-url.tokens';

// NOTE: Capacitor wraps plugins in a Proxy that bypasses Jasmine spies on
// property access AND ignores property assignment (no `set` trap, every `get`
// returns a fresh wrapper). We therefore inject a tiny `PushNotificationsAdapter`
// facade and assert behaviour against that adapter + the DeviceTokenApi.
describe('PushNotificationsService', () => {
  let svc: PushNotificationsService;
  let apiSpy: jasmine.SpyObj<DeviceTokenApi>;
  let adapter: jasmine.SpyObj<PushNotificationsAdapter>;
  let capturedListener: ((t: Token) => void | Promise<void>) | null;

  beforeEach(() => {
    capturedListener = null;
    apiSpy = jasmine.createSpyObj<DeviceTokenApi>('DeviceTokenApi', ['sendDeviceToken']);
    apiSpy.sendDeviceToken.and.resolveTo();

    adapter = jasmine.createSpyObj<PushNotificationsAdapter>('PushNotificationsAdapter', [
      'requestPermissions',
      'register',
      'addListener',
    ]);
    adapter.addListener.and.callFake((_event, cb) => {
      capturedListener = cb;
      return Promise.resolve({ remove: () => Promise.resolve() });
    });

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: 'http://test' },
        { provide: DeviceTokenApi, useValue: apiSpy },
        { provide: PUSH_NOTIFICATIONS_ADAPTER, useValue: adapter },
      ],
    });
    svc = TestBed.inject(PushNotificationsService);
  });

  it('ensurePermission() no-ops on web platform', async () => {
    spyOn(Capacitor, 'getPlatform').and.returnValue('web');

    await svc.ensurePermission();

    expect(adapter.requestPermissions).not.toHaveBeenCalled();
    expect(adapter.register).not.toHaveBeenCalled();
    expect(apiSpy.sendDeviceToken).not.toHaveBeenCalled();
  });

  it('ensurePermission() requests + registers on native and forwards token to API', async () => {
    spyOn(Capacitor, 'getPlatform').and.returnValue('ios');
    adapter.requestPermissions.and.resolveTo({ receive: 'granted' });
    adapter.register.and.resolveTo();

    await svc.ensurePermission();

    expect(adapter.requestPermissions).toHaveBeenCalled();
    expect(adapter.register).toHaveBeenCalled();
    expect(capturedListener).not.toBeNull();

    await capturedListener!({ value: 'device-token-xyz' } as Token);
    expect(apiSpy.sendDeviceToken).toHaveBeenCalledWith('device-token-xyz', 'ios');
  });
});
