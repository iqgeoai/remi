import { createActionGroup, props, emptyProps } from '@ngrx/store';
import { LobbyGame, ApiError } from '../../core/models';
import { QuickMatchRequest } from '../../core/api/matchmaking.api';

export const Match = createActionGroup({
  source: 'Match',
  events: {
    'Quick Requested': props<{ req: QuickMatchRequest }>(),
    'Queued': emptyProps(),
    'Matched': props<{ game: LobbyGame }>(),
    'Quick Failed': props<{ error: ApiError }>(),

    'Cancel Requested': emptyProps(),
    'Cancelled': emptyProps(),

    'Subscribe To Match Topic': emptyProps(),    // triggers subscribeToMatches via effect
  },
});
