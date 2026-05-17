export interface MeldProposal {
  type: 'GROUP' | 'SUITE';
  pieceIds: number[];
}

export interface LayoffProposal {
  pieceId: number;
  meldIdx: number;
}

export type Action =
  | { type: 'DRAW_FROM_STOCK'; playerIdx: number }
  | { type: 'TAKE_DISCARD'; playerIdx: number; discardIdx: number }
  | { type: 'ETALAT'; playerIdx: number; melds: MeldProposal[] }
  | { type: 'LAYOFF'; playerIdx: number; layoffs: LayoffProposal[] }
  | { type: 'DISCARD'; playerIdx: number; pieceId: number }
  | { type: 'FORCE_AUTO'; playerIdx: number };

export type ActionType = Action['type'];
