import { matchReducer, initialMatchState } from './match.reducer';
import { Match } from './match.actions';
import { MatchEffects } from './match.effects';
import { selectMatchStatus, selectMatchedGame, selectMatchError } from './match.selectors';

describe('matchReducer', () => {
  it('queued → status queued', () => {
    const s = matchReducer(initialMatchState, Match.queued());
    expect(s.status).toBe('queued');
  });
  it('matched → status matched + matchedGame', () => {
    const game = { id: 'g1' } as any;
    const s = matchReducer(initialMatchState, Match.matched({ game }));
    expect(s.status).toBe('matched');
    expect(s.matchedGame).toBe(game);
  });
  it('cancelled → initial', () => {
    const dirty = { ...initialMatchState, status: 'queued' as const };
    expect(matchReducer(dirty, Match.cancelled())).toEqual(initialMatchState);
  });

  it('selectors + effects class are wired', () => {
    expect(selectMatchStatus).toBeDefined();
    expect(selectMatchedGame).toBeDefined();
    expect(selectMatchError).toBeDefined();
    expect(MatchEffects).toBeDefined();
  });
});
