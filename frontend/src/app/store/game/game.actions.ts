import { createActionGroup, props, emptyProps } from '@ngrx/store';
import { GameView, Action, ApiError, DomainEvent } from '../../core/models';

export const Game = createActionGroup({
  source: 'Game',
  events: {
    'Subscribe To Game': props<{ gameId: string }>(),
    'View Received': props<{ view: GameView; events: DomainEvent[] }>(),

    'Subscribe To Errors': emptyProps(),
    'Error Received': props<{ error: ApiError }>(),

    'Send Action': props<{ gameId: string; action: Action }>(),

    'Load Game Requested': props<{ gameId: string }>(),    // fallback REST GET
    'Load Game Succeeded': props<{ view: GameView }>(),
    'Load Game Failed': props<{ error: ApiError }>(),

    'Clear Game': emptyProps(),
  },
});
