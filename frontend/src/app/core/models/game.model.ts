import { Mode, Difficulty } from './lobby.model';

export type PieceColor = 'RED' | 'YELLOW' | 'BLUE' | 'BLACK' | 'JOKER';
export type Phase = 'DRAW' | 'ACTION' | 'DISCARD';
export type DrawSource = 'STOCK' | 'DISCARD';

export interface Piece {
  id: number;
  num: number;
  color: PieceColor;
  isJoker: boolean;
}

export interface PlayerView {
  name: string;
  isBot: boolean;
  hasEtalat: boolean;
  calledAtu: boolean;
  announced: boolean;
  mustUsePieceId: number | null;
  hand: Piece[];
  handCount: number;
}

export interface Meld {
  owner: number;
  type: 'GROUP' | 'SUITE';
  pieces: Piece[];
  placedBy: Record<number, number>;
}

export interface GameView {
  id: string;
  players: PlayerView[];
  stockCount: number;
  discard: Piece[];
  atu: Piece;
  melds: Meld[];
  current: number;
  phase: Phase;
  drewFrom: DrawSource | null;
  turnTaken: number;
  round: number;
  mode: Mode;
  difficulty: Difficulty;
  doubleGame: boolean;
  closed: boolean;
  totals: number[];
}

export interface DomainEvent {
  type: string;
  [key: string]: unknown;
}
