import { createFeature, createReducer, on } from '@ngrx/store';
import { Match } from './match.actions';
import { LobbyGame, ApiError } from '../../core/models';

export type MatchStatus = 'idle' | 'queued' | 'matched' | 'error';

export interface MatchState {
  status: MatchStatus;
  matchedGame: LobbyGame | null;
  error: ApiError | null;
}

export const initialMatchState: MatchState = { status: 'idle', matchedGame: null, error: null };

export const matchReducer = createReducer(
  initialMatchState,
  on(Match.quickRequested, s => ({ ...s, status: 'idle' as const, error: null })),
  on(Match.queued, s => ({ ...s, status: 'queued' as const })),
  on(Match.matched, (s, { game }) => ({ ...s, status: 'matched' as const, matchedGame: game })),
  on(Match.quickFailed, (s, { error }) => ({ ...s, status: 'error' as const, error })),
  on(Match.cancelled, () => initialMatchState),
);

export const matchFeature = createFeature({ name: 'match', reducer: matchReducer });
