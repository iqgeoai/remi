import { detectMeld } from './meld-detection';
import { Piece } from '../../../core/models';

const p = (id: number, num: number, color: 'RED'|'YELLOW'|'BLUE'|'BLACK'): Piece =>
  ({ id, num, color, isJoker: false });
const joker = (id: number): Piece => ({ id, num: 0, color: 'JOKER', isJoker: true });

describe('detectMeld', () => {
  it('detects GROUP of 3 same num different colors', () => {
    const hand = [p(1,7,'RED'), p(2,7,'BLUE'), p(3,7,'BLACK')];
    const result = detectMeld(hand, new Set([1,2,3]));
    expect(result).toEqual({ type: 'GROUP', pieceIds: [1,2,3] });
  });

  it('detects GROUP of 4 different colors', () => {
    const hand = [p(1,7,'RED'), p(2,7,'BLUE'), p(3,7,'BLACK'), p(4,7,'YELLOW')];
    expect(detectMeld(hand, new Set([1,2,3,4]))?.type).toBe('GROUP');
  });

  it('rejects GROUP with duplicate color', () => {
    const hand = [p(1,7,'RED'), p(2,7,'RED'), p(3,7,'BLUE')];
    expect(detectMeld(hand, new Set([1,2,3]))).toBeNull();
  });

  it('rejects size < 3', () => {
    const hand = [p(1,7,'RED'), p(2,7,'BLUE')];
    expect(detectMeld(hand, new Set([1,2]))).toBeNull();
  });

  it('detects SUITE same color consecutive', () => {
    const hand = [p(1,5,'RED'), p(2,6,'RED'), p(3,7,'RED')];
    const result = detectMeld(hand, new Set([1,2,3]));
    expect(result?.type).toBe('SUITE');
    expect(result?.pieceIds).toEqual([1,2,3]);
  });

  it('detects SUITE with wrap 12-13-1', () => {
    const hand = [p(1,12,'RED'), p(2,13,'RED'), p(3,1,'RED')];
    expect(detectMeld(hand, new Set([1,2,3]))?.type).toBe('SUITE');
  });

  it('rejects SUITE with gap', () => {
    const hand = [p(1,5,'RED'), p(2,7,'RED'), p(3,8,'RED')];
    expect(detectMeld(hand, new Set([1,2,3]))).toBeNull();
  });

  it('rejects SUITE mixed colors', () => {
    const hand = [p(1,5,'RED'), p(2,6,'BLUE'), p(3,7,'RED')];
    expect(detectMeld(hand, new Set([1,2,3]))).toBeNull();
  });

  it('detects SUITE with joker in middle', () => {
    const hand = [p(1,5,'RED'), joker(2), p(3,7,'RED')];
    expect(detectMeld(hand, new Set([1,2,3]))?.type).toBe('SUITE');
  });

  it('detects GROUP with one joker', () => {
    const hand = [p(1,7,'RED'), p(2,7,'BLUE'), joker(3)];
    expect(detectMeld(hand, new Set([1,2,3]))?.type).toBe('GROUP');
  });

  it('returns null for selected ids not in hand', () => {
    const hand = [p(1,5,'RED'), p(2,6,'RED'), p(3,7,'RED')];
    expect(detectMeld(hand, new Set([1,2,999]))).toBeNull();
  });

  it('returns null for selection size > 13 (impossible SUITE length)', () => {
    const hand: Piece[] = [];
    for (let i = 0; i < 14; i++) hand.push(p(i, (i%13)+1, 'RED'));
    expect(detectMeld(hand, new Set(hand.map(x => x.id)))).toBeNull();
  });
});
