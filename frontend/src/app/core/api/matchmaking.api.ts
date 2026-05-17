import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URL } from '../config/api-url.tokens';
import { LobbyGame, Mode, Difficulty } from '../models';

export interface QuickMatchRequest {
  numPlayers: number;
  mode: Mode;
  difficulty: Difficulty;
}

export interface QuickMatchResponse {
  matched: boolean;
  game?: LobbyGame;
}

@Injectable({ providedIn: 'root' })
export class MatchmakingApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_URL);

  quick(req: QuickMatchRequest): Observable<QuickMatchResponse> {
    return this.http.post<QuickMatchResponse>(`${this.base}/matchmaking/quick`, req);
  }

  cancel(): Observable<void> {
    return this.http.post<void>(`${this.base}/matchmaking/cancel`, {});
  }
}
