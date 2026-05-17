import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { StompService } from './stomp.service';
import { GameView, DomainEvent, Action, ApiError, LobbyGame } from '../models';

export interface GameUpdate {
  view: GameView;
  events: DomainEvent[];
}

@Injectable({ providedIn: 'root' })
export class GameWsService {
  private readonly stomp = inject(StompService);

  subscribeToGame(gameId: string): Observable<GameUpdate> {
    return this.stomp.subscribe<GameUpdate>(`/user/queue/games/${gameId}`);
  }

  subscribeToErrors(): Observable<ApiError> {
    return this.stomp.subscribe<ApiError>('/user/queue/errors');
  }

  subscribeToMatches(): Observable<LobbyGame> {
    return this.stomp.subscribe<LobbyGame>('/user/queue/match');
  }

  sendAction(gameId: string, action: Action): void {
    this.stomp.send(`/app/games/${gameId}/actions`, action);
  }
}
