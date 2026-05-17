import { lobbyFeature } from './lobby.reducer';

export const {
  selectPublicGames,
  selectMyGames,
  selectLoading: selectLobbyLoading,
  selectError: selectLobbyError,
} = lobbyFeature;
