import { GameView, Piece, PlayerView } from '../app/core/models';

export const TEST_PIECES: Record<string, Piece> = {
  red1:  { id: 1,  num: 1,  color: 'RED',    isJoker: false },
  red5:  { id: 2,  num: 5,  color: 'RED',    isJoker: false },
  red6:  { id: 3,  num: 6,  color: 'RED',    isJoker: false },
  red7:  { id: 4,  num: 7,  color: 'RED',    isJoker: false },
  red12: { id: 5,  num: 12, color: 'RED',    isJoker: false },
  red13: { id: 6,  num: 13, color: 'RED',    isJoker: false },
  blue7: { id: 7,  num: 7,  color: 'BLUE',   isJoker: false },
  black7:{ id: 8,  num: 7,  color: 'BLACK',  isJoker: false },
  yel10: { id: 9,  num: 10, color: 'YELLOW', isJoker: false },
  blu10: { id: 10, num: 10, color: 'BLUE',   isJoker: false },
  blk10: { id: 11, num: 10, color: 'BLACK',  isJoker: false },
  joker1:{ id: 12, num: 0,  color: 'JOKER',  isJoker: true  },
  joker2:{ id: 13, num: 0,  color: 'JOKER',  isJoker: true  },
};

export const TEST_PLAYER_ALICE: PlayerView = {
  name: 'Tu',
  isBot: false,
  hasEtalat: false,
  calledAtu: false,
  announced: false,
  mustUsePieceId: null,
  hand: [TEST_PIECES['red5'], TEST_PIECES['red6'], TEST_PIECES['red7'],
         TEST_PIECES['yel10'], TEST_PIECES['blu10'], TEST_PIECES['blk10']],
  handCount: 6,
};

export const TEST_PLAYER_BOB: PlayerView = {
  name: 'Bob',
  isBot: false,
  hasEtalat: false,
  calledAtu: false,
  announced: false,
  mustUsePieceId: null,
  hand: [],
  handCount: 14,
};

export const TEST_GAME_VIEW: GameView = {
  id: 'test-game-1',
  players: [TEST_PLAYER_ALICE, TEST_PLAYER_BOB],
  stockCount: 80,
  discard: [TEST_PIECES['red1']],
  atu: TEST_PIECES['red5'],
  melds: [],
  current: 0,
  phase: 'DISCARD',
  drewFrom: 'STOCK',
  turnTaken: 0,
  round: 1,
  mode: 'ETALAT',
  difficulty: 'MED',
  doubleGame: false,
  closed: false,
  totals: [0, 0],
};
