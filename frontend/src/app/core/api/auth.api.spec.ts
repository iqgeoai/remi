import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AuthApi } from './auth.api';
import { environment } from '../../../environments/environment';

describe('AuthApi', () => {
  let api: AuthApi;
  let httpMock: HttpTestingController;
  const base = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), AuthApi],
    });
    api = TestBed.inject(AuthApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('register POSTs to /auth/register', () => {
    const body = { email: 'a@b.com', username: 'alice', password: 'passwordxx' };
    api.register(body).subscribe();
    const req = httpMock.expectOne(`${base}/auth/register`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ id: '1', email: 'a@b.com', username: 'alice', emailVerified: false, createdAt: '2026-01-01' });
  });

  it('login POSTs to /auth/login', () => {
    api.login({ emailOrUsername: 'alice', password: 'passwordxx' }).subscribe();
    const req = httpMock.expectOne(`${base}/auth/login`);
    expect(req.request.method).toBe('POST');
    req.flush({ accessToken: 'a', refreshToken: 'r', accessExpiresAt: '2026-01-01' });
  });

  it('verifyEmail POSTs token in body', () => {
    api.verifyEmail('t-1').subscribe();
    const req = httpMock.expectOne(`${base}/auth/verify-email`);
    expect(req.request.body).toEqual({ token: 't-1' });
    req.flush(null);
  });

  it('refresh POSTs refreshToken', () => {
    api.refresh('r-1').subscribe();
    const req = httpMock.expectOne(`${base}/auth/refresh`);
    expect(req.request.body).toEqual({ refreshToken: 'r-1' });
    req.flush({ accessToken: 'a2', refreshToken: 'r2', accessExpiresAt: '2026-01-02' });
  });

  it('logout POSTs refreshToken', () => {
    api.logout('r-1').subscribe();
    const req = httpMock.expectOne(`${base}/auth/logout`);
    expect(req.request.body).toEqual({ refreshToken: 'r-1' });
    req.flush(null);
  });

  it('requestPasswordReset POSTs email', () => {
    api.requestPasswordReset('a@b.com').subscribe();
    const req = httpMock.expectOne(`${base}/auth/request-password-reset`);
    expect(req.request.body).toEqual({ email: 'a@b.com' });
    req.flush(null);
  });

  it('resetPassword POSTs token + newPassword', () => {
    api.resetPassword('t-1', 'newpasswordxx').subscribe();
    const req = httpMock.expectOne(`${base}/auth/reset-password`);
    expect(req.request.body).toEqual({ token: 't-1', newPassword: 'newpasswordxx' });
    req.flush(null);
  });

  it('me GETs /users/me', () => {
    api.me().subscribe();
    const req = httpMock.expectOne(`${base}/users/me`);
    expect(req.request.method).toBe('GET');
    req.flush({ id: '1', email: 'a@b.com', username: 'alice', emailVerified: true, createdAt: '2026-01-01' });
  });
});
