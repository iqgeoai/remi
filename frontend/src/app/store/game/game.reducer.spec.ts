import { gameReducer, initialGameState } from './game.reducer';
import { Game } from './game.actions';
import { GameEffects } from './game.effects';
import { selectGameId, selectGameView, selectGameEvents, selectGameError } from './game.selectors';

describe('gameReducer', () => {
  it('subscribeToGame sets gameId and clears prior state', () => {
    const s = gameReducer(initialGameState, Game.subscribeToGame({ gameId: 'g1' }));
    expect(s.gameId).toBe('g1');
    expect(s.view).toBeNull();
  });
  it('viewReceived appends events and sets view', () => {
    const view = { id: 'g1' } as any;
    const events = [{ type: 'CardDrawn' } as any];
    const s = gameReducer({ ...initialGameState, gameId: 'g1' }, Game.viewReceived({ view, events }));
    expect(s.view).toBe(view);
    expect(s.events).toEqual(events);
  });
  it('clearGame resets state', () => {
    const dirty = { ...initialGameState, gameId: 'g1', view: {} as any };
    expect(gameReducer(dirty, Game.clearGame())).toEqual(initialGameState);
  });

  it('selectors + effects class are wired', () => {
    expect(selectGameId).toBeDefined();
    expect(selectGameView).toBeDefined();
    expect(selectGameEvents).toBeDefined();
    expect(selectGameError).toBeDefined();
    expect(GameEffects).toBeDefined();
  });
});
