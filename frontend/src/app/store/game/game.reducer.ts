import { createFeature, createReducer, on } from '@ngrx/store';
import { Game } from './game.actions';
import { GameView, DomainEvent, ApiError } from '../../core/models';

export interface GameState {
  gameId: string | null;
  view: GameView | null;
  events: DomainEvent[];
  error: ApiError | null;
}

export const initialGameState: GameState = { gameId: null, view: null, events: [], error: null };

export const gameReducer = createReducer(
  initialGameState,
  on(Game.subscribeToGame, (s, { gameId }) => ({ ...initialGameState, gameId })),
  on(Game.viewReceived, (s, { view, events }) => ({ ...s, view, events: [...s.events, ...events] })),
  on(Game.loadGameSucceeded, (s, { view }) => ({ ...s, view })),
  on(Game.loadGameFailed, (s, { error }) => ({ ...s, error })),
  on(Game.errorReceived, (s, { error }) => ({ ...s, error })),
  on(Game.clearGame, () => initialGameState),
);

export const gameFeature = createFeature({ name: 'game', reducer: gameReducer });
