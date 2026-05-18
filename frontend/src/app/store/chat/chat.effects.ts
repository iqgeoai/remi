import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { from } from 'rxjs';
import { map, mergeMap, switchMap } from 'rxjs/operators';
import { ChatActions } from './chat.actions';
import { ChatApi } from '../../core/api/chat.api';
import { dmKey, matchKey } from './chat.models';

@Injectable()
export class ChatEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(ChatApi);

  loadMatchHistory$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ChatActions.loadMatchHistory),
      switchMap(({ matchId }) =>
        from(this.api.matchHistory(matchId)).pipe(
          map(messages =>
            ChatActions.historyLoaded({ channelKey: matchKey(matchId), messages }),
          ),
        ),
      ),
    ),
  );

  loadDmHistory$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ChatActions.loadDmHistory),
      switchMap(({ otherUserId }) =>
        from(this.api.dmHistory(otherUserId)).pipe(
          map(messages =>
            ChatActions.historyLoaded({ channelKey: dmKey(otherUserId), messages }),
          ),
        ),
      ),
    ),
  );

  sendMatch$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(ChatActions.sendMatchMessage),
        mergeMap(({ matchId, body }) => from(this.api.sendMatch(matchId, body))),
      ),
    { dispatch: false },
  );

  sendDm$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(ChatActions.sendDmMessage),
        mergeMap(({ otherUserId, body }) => from(this.api.sendDm(otherUserId, body))),
      ),
    { dispatch: false },
  );

  loadConversations$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ChatActions.loadConversations),
      switchMap(() =>
        from(this.api.conversations()).pipe(
          map(conversations => ChatActions.conversationsLoaded({ conversations })),
        ),
      ),
    ),
  );
}
