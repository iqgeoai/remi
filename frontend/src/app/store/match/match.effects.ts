import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, exhaustMap, map, of, switchMap, tap } from 'rxjs';
import { Match } from './match.actions';
import { MatchmakingApi } from '../../core/api/matchmaking.api';
import { GameWsService } from '../../core/ws/game-ws.service';
import { ApiError } from '../../core/models';

const toApiError = (err: HttpErrorResponse): ApiError =>
  (err.error && typeof err.error === 'object' && 'code' in err.error)
    ? (err.error as ApiError)
    : { code: 'NETWORK', message: 'Eroare de rețea.' };

@Injectable()
export class MatchEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(MatchmakingApi);
  private readonly ws = inject(GameWsService);
  private readonly router = inject(Router);

  subscribeToMatchTopic$ = createEffect(() => this.actions$.pipe(
    ofType(Match.subscribeToMatchTopic),
    switchMap(() => this.ws.subscribeToMatches().pipe(
      map(game => Match.matched({ game })),
    )),
  ));

  quick$ = createEffect(() => this.actions$.pipe(
    ofType(Match.quickRequested),
    exhaustMap(({ req }) => this.api.quick(req).pipe(
      map(resp => resp.matched && resp.game
          ? Match.matched({ game: resp.game })
          : Match.queued()),
      catchError(err => of(Match.quickFailed({ error: toApiError(err) }))),
    )),
  ));

  cancel$ = createEffect(() => this.actions$.pipe(
    ofType(Match.cancelRequested),
    exhaustMap(() => this.api.cancel().pipe(
      map(() => Match.cancelled()),
      catchError(() => of(Match.cancelled())),
    )),
  ));

  navigateOnMatched$ = createEffect(() => this.actions$.pipe(
    ofType(Match.matched),
    tap(({ game }) => this.router.navigateByUrl(`/game/${game.id}`)),
  ), { dispatch: false });
}
