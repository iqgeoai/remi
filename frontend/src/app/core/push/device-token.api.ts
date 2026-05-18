import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../config/api-url.tokens';

@Injectable({ providedIn: 'root' })
export class DeviceTokenApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_URL);

  async sendDeviceToken(token: string, platform: 'ios' | 'android'): Promise<void> {
    await firstValueFrom(
      this.http.post<void>(`${this.base}/push/device-token`, { token, platform }),
    );
  }
}
