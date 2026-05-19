import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { from } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';
import { StatsActions } from './stats.actions';
import { StatsApi } from '../../core/api/stats.api';

@Injectable()
export class StatsEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(StatsApi);

  loadProfile$ = createEffect(() =>
    this.actions$.pipe(
      ofType(StatsActions.loadProfile),
      switchMap(({ userId }) =>
        from(this.api.profile(userId)).pipe(
          map(profile => StatsActions.profileLoaded({ profile })),
        ),
      ),
    ),
  );

  loadMyStats$ = createEffect(() =>
    this.actions$.pipe(
      ofType(StatsActions.loadMyStats),
      switchMap(() =>
        from(this.api.myStats()).pipe(
          map(profile => StatsActions.profileLoaded({ profile })),
        ),
      ),
    ),
  );

  loadLeaderboard$ = createEffect(() =>
    this.actions$.pipe(
      ofType(StatsActions.loadLeaderboard),
      switchMap(() =>
        from(this.api.leaderboard()).pipe(
          map(entries => StatsActions.leaderboardLoaded({ entries })),
        ),
      ),
    ),
  );
}
