import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import { tap, withLatestFrom } from 'rxjs';
import { Auth } from './auth.actions';
import { StompService } from '../../core/ws/stomp.service';
import { selectTokens } from './auth.selectors';

@Injectable()
export class AuthWsEffects {
  private readonly actions$ = inject(Actions);
  private readonly stomp = inject(StompService);
  private readonly store = inject(Store);

  connectAfterLogin$ = createEffect(() => this.actions$.pipe(
    ofType(Auth.userLoaded, Auth.bootstrapSucceeded),
    withLatestFrom(this.store.select(selectTokens)),
    tap(([_, tokens]) => {
      if (tokens) this.stomp.connect(tokens.accessToken);
    }),
  ), { dispatch: false });

  disconnectOnLogout$ = createEffect(() => this.actions$.pipe(
    ofType(Auth.logoutLocal, Auth.sessionInvalidated, Auth.refreshFailed),
    tap(() => this.stomp.disconnect()),
  ), { dispatch: false });
}
