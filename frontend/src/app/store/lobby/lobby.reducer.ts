import { createFeature, createReducer, on } from '@ngrx/store';
import { Lobby } from './lobby.actions';
import { LobbyGame, ApiError } from '../../core/models';

export interface LobbyState {
  publicGames: LobbyGame[];
  myGames: LobbyGame[];
  loading: boolean;
  error: ApiError | null;
}

export const initialLobbyState: LobbyState = {
  publicGames: [],
  myGames: [],
  loading: false,
  error: null,
};

export const lobbyReducer = createReducer(
  initialLobbyState,
  on(Lobby.createRequested, Lobby.joinByCodeRequested, Lobby.joinPublicRequested,
     Lobby.listPublicRequested, Lobby.myGamesRequested, Lobby.leaveRequested,
     s => ({ ...s, loading: true, error: null })),
  on(Lobby.createFailed, Lobby.joinByCodeFailed, Lobby.joinPublicFailed,
     Lobby.listPublicFailed, Lobby.myGamesFailed, Lobby.leaveFailed,
     (s, { error }) => ({ ...s, loading: false, error })),
  on(Lobby.listPublicSucceeded, (s, { games }) => ({ ...s, loading: false, publicGames: games })),
  on(Lobby.myGamesSucceeded, (s, { games }) => ({ ...s, loading: false, myGames: games })),
  on(Lobby.createSucceeded, Lobby.joinByCodeSucceeded, Lobby.joinPublicSucceeded,
     s => ({ ...s, loading: false })),
  on(Lobby.leaveSucceeded, (s, { gameId }) =>
    ({ ...s, loading: false, myGames: s.myGames.filter(g => g.id !== gameId) })),
);

export const lobbyFeature = createFeature({
  name: 'lobby',
  reducer: lobbyReducer,
});
