import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URL } from '../config/api-url.tokens';
import { User, AuthTokens } from '../models';

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_URL);

  register(req: { email: string; username: string; password: string }): Observable<User> {
    return this.http.post<User>(`${this.base}/auth/register`, req);
  }

  verifyEmail(token: string): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/verify-email`, { token });
  }

  login(req: { emailOrUsername: string; password: string }): Observable<AuthTokens> {
    return this.http.post<AuthTokens>(`${this.base}/auth/login`, req);
  }

  refresh(refreshToken: string): Observable<AuthTokens> {
    return this.http.post<AuthTokens>(`${this.base}/auth/refresh`, { refreshToken });
  }

  logout(refreshToken: string): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/logout`, { refreshToken });
  }

  requestPasswordReset(email: string): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/request-password-reset`, { email });
  }

  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/reset-password`, { token, newPassword });
  }

  me(): Observable<User> {
    return this.http.get<User>(`${this.base}/users/me`);
  }
}
