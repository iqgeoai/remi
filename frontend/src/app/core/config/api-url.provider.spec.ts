import { Capacitor } from '@capacitor/core';
import { resolveApiUrl, resolveWsUrl } from './api-url.provider';

describe('api-url.provider', () => {
  let getPlatformSpy: jasmine.Spy;

  beforeEach(() => {
    getPlatformSpy = spyOn(Capacitor, 'getPlatform');
  });

  describe('resolveApiUrl()', () => {
    it('returns "/api" for web platform', () => {
      getPlatformSpy.and.returnValue('web');
      expect(resolveApiUrl()).toBe('/api');
    });

    it('returns "http://localhost:8080/api" for iOS platform', () => {
      getPlatformSpy.and.returnValue('ios');
      expect(resolveApiUrl()).toBe('http://localhost:8080/api');
    });

    it('returns "http://10.0.2.2:8080/api" for Android platform', () => {
      getPlatformSpy.and.returnValue('android');
      expect(resolveApiUrl()).toBe('http://10.0.2.2:8080/api');
    });
  });

  describe('resolveWsUrl()', () => {
    it('returns "/ws" for web platform', () => {
      getPlatformSpy.and.returnValue('web');
      expect(resolveWsUrl()).toBe('/ws');
    });

    it('returns "http://localhost:8080/ws" for iOS platform', () => {
      getPlatformSpy.and.returnValue('ios');
      expect(resolveWsUrl()).toBe('http://localhost:8080/ws');
    });

    it('returns "http://10.0.2.2:8080/ws" for Android platform', () => {
      getPlatformSpy.and.returnValue('android');
      expect(resolveWsUrl()).toBe('http://10.0.2.2:8080/ws');
    });
  });
});
