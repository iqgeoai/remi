import { Piece, MeldProposal } from '../../../core/models';

/**
 * Given a hand and a set of selected piece IDs, infer if they form a valid meld.
 * Returns a MeldProposal with type GROUP or SUITE, or null if invalid.
 * Used to enable/disable the "Etalează" button. Server validates anyway.
 *
 * Mirrors backend MeldValidator logic (assets/remi.html:823-883 and
 * com.remi.engine.rules.MeldValidator).
 */
export function detectMeld(hand: Piece[], selectedIds: Set<number>): MeldProposal | null {
  if (selectedIds.size < 3 || selectedIds.size > 13) return null;

  const pieces: Piece[] = [];
  for (const piece of hand) {
    if (selectedIds.has(piece.id)) pieces.push(piece);
  }
  if (pieces.length !== selectedIds.size) return null;   // some ids not in hand

  const jokers = pieces.filter(p => p.isJoker);
  const reals = pieces.filter(p => !p.isJoker);
  if (reals.length < 2) return null;
  if (jokers.length > reals.length) return null;

  if (tryGroup(reals, pieces.length)) {
    return { type: 'GROUP', pieceIds: pieces.map(p => p.id) };
  }
  // For SUITE we need to find a valid ordering. Sort reals by num, place jokers in gaps.
  const ordered = tryOrderForSuite(pieces);
  if (ordered !== null) {
    return { type: 'SUITE', pieceIds: ordered.map(p => p.id) };
  }
  return null;
}

function tryGroup(reals: Piece[], totalSize: number): boolean {
  if (totalSize > 4) return false;
  const num = reals[0].num;
  if (!reals.every(p => p.num === num)) return false;
  const colors = new Set(reals.map(p => p.color));
  return colors.size === reals.length;
}

/**
 * Try to arrange pieces as a valid suite (same color, consecutive nums,
 * jokers fill gaps). Allows 12-13-1 wrap. Returns the ordered list or null.
 */
function tryOrderForSuite(pieces: Piece[]): Piece[] | null {
  const reals = pieces.filter(p => !p.isJoker);
  const color = reals[0].color;
  if (!reals.every(p => p.color === color)) return null;

  // Sort reals by num
  const sortedReals = [...reals].sort((a, b) => a.num - b.num);

  // Try non-wrap arrangement first
  for (let base = 1; base <= 13; base++) {
    const arrangement = tryArrange(sortedReals, pieces.filter(p => p.isJoker), base, pieces.length, false);
    if (arrangement !== null) return arrangement;
  }
  // Try wrap arrangement (12-13-1 etc.)
  for (let base = 1; base <= 13; base++) {
    const arrangement = tryArrange(sortedReals, pieces.filter(p => p.isJoker), base, pieces.length, true);
    if (arrangement !== null) return arrangement;
  }
  return null;
}

function tryArrange(reals: Piece[], jokers: Piece[], base: number, size: number, wrap: boolean): Piece[] | null {
  if (base + size - 1 > 14) return null;        // overshoots 14
  if (!wrap && base + size - 1 > 13) return null;
  if (wrap && base + size - 1 !== 14) return null;  // only allow wrap when last position is exactly 14

  // Position i corresponds to number ((base + i - 1) % 13) + 1 ... simplified for wrap=14 case
  const slots: (Piece | null)[] = new Array(size).fill(null);
  const jokerPool = [...jokers];

  for (const real of reals) {
    let slotIdx = real.num - base;
    if (slotIdx < 0 && wrap) slotIdx += 13;
    if (slotIdx < 0 || slotIdx >= size) return null;
    if (slots[slotIdx] !== null) return null;
    slots[slotIdx] = real;
  }
  for (let i = 0; i < size; i++) {
    if (slots[i] === null) {
      const j = jokerPool.shift();
      if (!j) return null;
      slots[i] = j;
    }
  }
  return slots.filter((p): p is Piece => p !== null);
}
