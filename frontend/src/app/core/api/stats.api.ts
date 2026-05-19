import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../config/api-url.tokens';
import { Profile, LeaderboardEntry } from '../../store/stats/stats.models';

@Injectable({ providedIn: 'root' })
export class StatsApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_URL);

  profile(userId: string): Promise<Profile> {
    return firstValueFrom(this.http.get<Profile>(`${this.base}/users/${userId}/profile`));
  }

  myStats(): Promise<Profile> {
    return firstValueFrom(this.http.get<Profile>(`${this.base}/users/me/stats`));
  }

  leaderboard(): Promise<LeaderboardEntry[]> {
    return firstValueFrom(
      this.http.get<LeaderboardEntry[]>(`${this.base}/leaderboard?limit=50`),
    );
  }
}
