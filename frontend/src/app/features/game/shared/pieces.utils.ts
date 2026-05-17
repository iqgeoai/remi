import { Piece, PieceColor } from '../../../core/models';

const COLOR_LABELS: Record<PieceColor, string> = {
  RED: 'Roșu',
  YELLOW: 'Galben',
  BLUE: 'Albastru',
  BLACK: 'Negru',
  JOKER: 'Joker',
};

export function getPieceColorClass(piece: Piece): string {
  return 'color-' + piece.color.toLowerCase();
}

export function formatPiece(piece: Piece): string {
  if (piece.isJoker) return 'Joker';
  return `${COLOR_LABELS[piece.color]} ${piece.num}`;
}
