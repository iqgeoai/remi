import { createActionGroup, props, emptyProps } from '@ngrx/store';
import { LobbyGame, ApiError } from '../../core/models';
import { CreateGameRequest } from '../../core/api/lobby.api';

export const Lobby = createActionGroup({
  source: 'Lobby',
  events: {
    'Create Requested': props<{ req: CreateGameRequest }>(),
    'Create Succeeded': props<{ game: LobbyGame }>(),
    'Create Failed': props<{ error: ApiError }>(),

    'Join By Code Requested': props<{ joinCode: string }>(),
    'Join By Code Succeeded': props<{ game: LobbyGame }>(),
    'Join By Code Failed': props<{ error: ApiError }>(),

    'Join Public Requested': props<{ gameId: string }>(),
    'Join Public Succeeded': props<{ game: LobbyGame }>(),
    'Join Public Failed': props<{ error: ApiError }>(),

    'List Public Requested': emptyProps(),
    'List Public Succeeded': props<{ games: LobbyGame[] }>(),
    'List Public Failed': props<{ error: ApiError }>(),

    'My Games Requested': emptyProps(),
    'My Games Succeeded': props<{ games: LobbyGame[] }>(),
    'My Games Failed': props<{ error: ApiError }>(),

    'Leave Requested': props<{ gameId: string }>(),
    'Leave Succeeded': props<{ gameId: string }>(),
    'Leave Failed': props<{ error: ApiError }>(),
  },
});
