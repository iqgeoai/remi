import { gameFeature } from './game.reducer';
export const {
  selectGameId,
  selectView: selectGameView,
  selectEvents: selectGameEvents,
  selectError: selectGameError,
} = gameFeature;
