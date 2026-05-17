import { getPieceColorClass, formatPiece } from './pieces.utils';
import { Piece } from '../../../core/models';

describe('pieces.utils', () => {
  const red5: Piece = { id: 1, num: 5, color: 'RED', isJoker: false };
  const joker: Piece = { id: 2, num: 0, color: 'JOKER', isJoker: true };
  const yellow13: Piece = { id: 3, num: 13, color: 'YELLOW', isJoker: false };

  describe('getPieceColorClass', () => {
    it('returns lowercase color class for non-joker', () => {
      expect(getPieceColorClass(red5)).toBe('color-red');
      expect(getPieceColorClass(yellow13)).toBe('color-yellow');
    });
    it('returns joker class for joker', () => {
      expect(getPieceColorClass(joker)).toBe('color-joker');
    });
  });

  describe('formatPiece', () => {
    it('formats non-joker as "<Color> <num>" in Romanian', () => {
      expect(formatPiece(red5)).toBe('Roșu 5');
      expect(formatPiece(yellow13)).toBe('Galben 13');
    });
    it('formats joker as "Joker"', () => {
      expect(formatPiece(joker)).toBe('Joker');
    });
  });
});
