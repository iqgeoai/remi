import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LobbyGame, GameView, Action, GameVisibility, Mode, Difficulty } from '../models';

export interface CreateGameRequest {
  visibility: GameVisibility;
  numPlayers: number;
  mode: Mode;
  difficulty: Difficulty;
}

@Injectable({ providedIn: 'root' })
export class LobbyApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  create(req: CreateGameRequest): Observable<LobbyGame> {
    return this.http.post<LobbyGame>(`${this.base}/games`, req);
  }

  joinByCode(joinCode: string): Observable<LobbyGame> {
    return this.http.post<LobbyGame>(`${this.base}/games/join-by-code`, { joinCode });
  }

  joinPublic(gameId: string): Observable<LobbyGame> {
    return this.http.post<LobbyGame>(`${this.base}/games/${gameId}/join`, {});
  }

  listPublic(): Observable<LobbyGame[]> {
    return this.http.get<LobbyGame[]>(`${this.base}/games/public`);
  }

  myGames(): Observable<LobbyGame[]> {
    return this.http.get<LobbyGame[]>(`${this.base}/games/mine`);
  }

  get(gameId: string): Observable<GameView> {
    return this.http.get<GameView>(`${this.base}/games/${gameId}`);
  }

  apply(gameId: string, action: Action): Observable<GameView> {
    return this.http.post<GameView>(`${this.base}/games/${gameId}/actions`, { action });
  }

  leave(gameId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/games/${gameId}/leave`, {});
  }
}
