import { lobbyReducer, initialLobbyState } from './lobby.reducer';
import { Lobby } from './lobby.actions';
import { LobbyGame } from '../../core/models';
import { lobbyFeature } from './lobby.feature';
import {
  selectPublicGames,
  selectMyGames,
  selectLobbyLoading,
  selectLobbyError,
} from './lobby.selectors';

const GAME: LobbyGame = {
  id: 'g1', ownerId: 'u1', visibility: 'PRIVATE', joinCode: 'CODE',
  numPlayers: 2, mode: 'ETALAT', difficulty: 'MED',
  seatsTaken: 1, started: false, createdAt: '2026-01-01',
};

describe('lobbyReducer', () => {
  it('createRequested sets loading + clears error', () => {
    const s = lobbyReducer(initialLobbyState, Lobby.createRequested({ req: {} as any }));
    expect(s.loading).toBeTrue();
    expect(s.error).toBeNull();
  });

  it('listPublicSucceeded fills publicGames', () => {
    const s = lobbyReducer(initialLobbyState, Lobby.listPublicSucceeded({ games: [GAME] }));
    expect(s.publicGames).toEqual([GAME]);
    expect(s.loading).toBeFalse();
  });

  it('leaveSucceeded removes from myGames', () => {
    const start = { ...initialLobbyState, myGames: [GAME] };
    const s = lobbyReducer(start, Lobby.leaveSucceeded({ gameId: 'g1' }));
    expect(s.myGames).toEqual([]);
  });

  it('createFailed records error', () => {
    const s = lobbyReducer(initialLobbyState, Lobby.createFailed({ error: { code: 'X', message: 'y' } }));
    expect(s.error?.code).toBe('X');
    expect(s.loading).toBeFalse();
  });

  it('selectors are wired to lobbyFeature', () => {
    expect(lobbyFeature.name).toBe('lobby');
    expect(selectPublicGames).toBeDefined();
    expect(selectMyGames).toBeDefined();
    expect(selectLobbyLoading).toBeDefined();
    expect(selectLobbyError).toBeDefined();
  });
});
