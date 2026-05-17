import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, exhaustMap, map, of, tap } from 'rxjs';
import { Lobby } from './lobby.actions';
import { LobbyApi } from '../../core/api/lobby.api';
import { ApiError } from '../../core/models';

const toApiError = (err: HttpErrorResponse): ApiError =>
  (err.error && typeof err.error === 'object' && 'code' in err.error)
    ? (err.error as ApiError)
    : { code: 'NETWORK', message: 'Eroare de rețea.' };

@Injectable()
export class LobbyEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(LobbyApi);
  private readonly router = inject(Router);

  create$ = createEffect(() => this.actions$.pipe(
    ofType(Lobby.createRequested),
    exhaustMap(({ req }) => this.api.create(req).pipe(
      map(game => Lobby.createSucceeded({ game })),
      catchError(err => of(Lobby.createFailed({ error: toApiError(err) }))),
    )),
  ));

  joinByCode$ = createEffect(() => this.actions$.pipe(
    ofType(Lobby.joinByCodeRequested),
    exhaustMap(({ joinCode }) => this.api.joinByCode(joinCode).pipe(
      map(game => Lobby.joinByCodeSucceeded({ game })),
      catchError(err => of(Lobby.joinByCodeFailed({ error: toApiError(err) }))),
    )),
  ));

  joinPublic$ = createEffect(() => this.actions$.pipe(
    ofType(Lobby.joinPublicRequested),
    exhaustMap(({ gameId }) => this.api.joinPublic(gameId).pipe(
      map(game => Lobby.joinPublicSucceeded({ game })),
      catchError(err => of(Lobby.joinPublicFailed({ error: toApiError(err) }))),
    )),
  ));

  listPublic$ = createEffect(() => this.actions$.pipe(
    ofType(Lobby.listPublicRequested),
    exhaustMap(() => this.api.listPublic().pipe(
      map(games => Lobby.listPublicSucceeded({ games })),
      catchError(err => of(Lobby.listPublicFailed({ error: toApiError(err) }))),
    )),
  ));

  myGames$ = createEffect(() => this.actions$.pipe(
    ofType(Lobby.myGamesRequested),
    exhaustMap(() => this.api.myGames().pipe(
      map(games => Lobby.myGamesSucceeded({ games })),
      catchError(err => of(Lobby.myGamesFailed({ error: toApiError(err) }))),
    )),
  ));

  leave$ = createEffect(() => this.actions$.pipe(
    ofType(Lobby.leaveRequested),
    exhaustMap(({ gameId }) => this.api.leave(gameId).pipe(
      map(() => Lobby.leaveSucceeded({ gameId })),
      catchError(err => of(Lobby.leaveFailed({ error: toApiError(err) }))),
    )),
  ));

  navigateOnJoinOrCreate$ = createEffect(() => this.actions$.pipe(
    ofType(Lobby.createSucceeded, Lobby.joinByCodeSucceeded, Lobby.joinPublicSucceeded),
    tap(({ game }) => this.router.navigateByUrl(`/game/${game.id}`)),
  ), { dispatch: false });
}
