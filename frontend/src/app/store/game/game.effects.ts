import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, exhaustMap, map, of, switchMap, tap } from 'rxjs';
import { Game } from './game.actions';
import { GameWsService } from '../../core/ws/game-ws.service';
import { LobbyApi } from '../../core/api/lobby.api';
import { ApiError } from '../../core/models';

const toApiError = (err: HttpErrorResponse): ApiError =>
  (err.error && typeof err.error === 'object' && 'code' in err.error)
    ? (err.error as ApiError)
    : { code: 'NETWORK', message: 'Eroare de rețea.' };

@Injectable()
export class GameEffects {
  private readonly actions$ = inject(Actions);
  private readonly ws = inject(GameWsService);
  private readonly api = inject(LobbyApi);

  subscribeToGame$ = createEffect(() => this.actions$.pipe(
    ofType(Game.subscribeToGame),
    switchMap(({ gameId }) => this.ws.subscribeToGame(gameId).pipe(
      map(({ view, events }) => Game.viewReceived({ view, events })),
    )),
  ));

  subscribeToErrors$ = createEffect(() => this.actions$.pipe(
    ofType(Game.subscribeToErrors),
    switchMap(() => this.ws.subscribeToErrors().pipe(
      map(error => Game.errorReceived({ error })),
    )),
  ));

  loadGame$ = createEffect(() => this.actions$.pipe(
    ofType(Game.loadGameRequested),
    exhaustMap(({ gameId }) => this.api.get(gameId).pipe(
      map(view => Game.loadGameSucceeded({ view })),
      catchError(err => of(Game.loadGameFailed({ error: toApiError(err) }))),
    )),
  ));

  sendAction$ = createEffect(() => this.actions$.pipe(
    ofType(Game.sendAction),
    tap(({ gameId, action }) => this.ws.sendAction(gameId, action)),
  ), { dispatch: false });
}
