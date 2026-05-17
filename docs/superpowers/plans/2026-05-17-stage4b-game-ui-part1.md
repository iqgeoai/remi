# Stage 4b — Game UI Implementation Plan (Part 1: Setup + Helpers + Atomic Components)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Stage 4a's `GameDebugPage` placeholder with a real game UI for Romanian Remi — opponents bar, table (atu/stock/discard/melds), draggable hand, turn timer, end-round modal — using Angular CDK Drag&Drop and Angular Animations.

**Architecture:** ~12 standalone Angular components under `frontend/src/app/features/game/components/`. NgRx Game store from Stage 4a reused unchanged. Local UI state (selectedIds, proposedMelds) as component signals. Mobile-first portrait layout. Spec: `docs/superpowers/specs/2026-05-17-stage4b-game-ui-design.md`.

**Tech Stack:** + `@angular/cdk` (drag-drop), `@angular/animations` (already in core). NO sound.

---

## File Structure (final, after both parts)

```
frontend/src/app/features/game/
  game.page.ts                  (replaces game-debug.page.ts)
  game.page.html
  game.page.scss
  game.page.spec.ts
  components/
    piece/
      piece.component.{ts,html,scss,spec.ts}
    hand/
      hand.component.{ts,html,scss,spec.ts}
    meld-card/
      meld-card.component.{ts,html,scss,spec.ts}
    stock-pile/
      stock-pile.component.{ts,html,scss,spec.ts}
    discard-pile/
      discard-pile.component.{ts,html,scss,spec.ts}
    atu-display/
      atu-display.component.{ts,html,scss,spec.ts}
    melds-area/
      melds-area.component.{ts,html,scss}
    table-zone/
      table-zone.component.{ts,html,scss}
    opponent-summary/
      opponent-summary.component.{ts,html,scss,spec.ts}
    opponents-bar/
      opponents-bar.component.{ts,html,scss,spec.ts}
    turn-timer/
      turn-timer.component.{ts,html,scss,spec.ts}
    action-bar/
      action-bar.component.{ts,html,scss,spec.ts}
    round-end-modal/
      round-end-modal.component.{ts,html,scss,spec.ts}
  shared/
    pieces.utils.ts
    pieces.utils.spec.ts
    meld-detection.ts
    meld-detection.spec.ts
  animations/
    game.animations.ts
frontend/src/test-utils/
  game-test-data.ts
frontend/SMOKE_TEST.md          (modify: add 6 scenarios)
```

---

## Phase A — Setup

### Task A1: Install `@angular/cdk`

**Files:** `frontend/package.json`, `frontend/package-lock.json`

- [ ] **Step 1: Install**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npm install @angular/cdk@^20
```

Match the Angular version installed (Stage 4a uses Angular 20).

- [ ] **Step 2: Verify build**

```bash
npx ng build --configuration=development
```

Expected: SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add frontend/package.json frontend/package-lock.json
git commit -m "build(frontend): add @angular/cdk for drag-drop"
```

---

## Phase B — Pure helpers

### Task B1: `pieces.utils.ts` + tests

**Files:**
- Create: `frontend/src/app/features/game/shared/pieces.utils.ts`
- Create: `frontend/src/app/features/game/shared/pieces.utils.spec.ts`

- [ ] **Step 1: Write test**

```typescript
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
```

- [ ] **Step 2: Run (FAIL — file missing)**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npx ng test --watch=false --browsers=ChromeHeadless --include='**/pieces.utils.spec.ts'
```

- [ ] **Step 3: Write `pieces.utils.ts`**

```typescript
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
```

- [ ] **Step 4: Run (PASS — 5 tests)** + commit

```bash
npx ng test --watch=false --browsers=ChromeHeadless --include='**/pieces.utils.spec.ts'
git add src/app/features/game/shared/pieces.utils.ts \
        src/app/features/game/shared/pieces.utils.spec.ts
git commit -m "feat(game): pieces.utils — getPieceColorClass + formatPiece (Romanian) + tests"
```

---

### Task B2: `meld-detection.ts` + extensive tests

**Files:**
- Create: `frontend/src/app/features/game/shared/meld-detection.ts`
- Create: `frontend/src/app/features/game/shared/meld-detection.spec.ts`

This is the most important pure helper — it powers the "Etalează" button enable/disable. Tests mirror backend `MeldValidator`.

- [ ] **Step 1: Write failing test**

```typescript
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
```

- [ ] **Step 2: Run (FAIL — file missing)**

- [ ] **Step 3: Write `meld-detection.ts`**

```typescript
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
```

- [ ] **Step 4: Run (PASS — 12 tests)** + commit

```bash
npx ng test --watch=false --browsers=ChromeHeadless --include='**/meld-detection.spec.ts'
git add src/app/features/game/shared/meld-detection.ts \
        src/app/features/game/shared/meld-detection.spec.ts
git commit -m "feat(game): meld-detection — client-side preview of GROUP/SUITE validity + tests"
```

If any test fails, the algorithm needs fixing. Don't move on until all 12 pass.

---

### Task B3: `game-test-data.ts` fixtures

**Files:**
- Create: `frontend/src/test-utils/game-test-data.ts`

- [ ] **Step 1: Write fixtures**

```typescript
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
```

- [ ] **Step 2: Build + commit**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npx ng build --configuration=development
git add src/test-utils/game-test-data.ts
git commit -m "test(game): shared test fixtures (TEST_PIECES, TEST_GAME_VIEW)"
```

---

## Phase C — Atomic visual components

### Task C1: `PieceComponent` + tests

**Files:**
- Create: `frontend/src/app/features/game/components/piece/piece.component.{ts,html,scss,spec.ts}`

- [ ] **Step 1: Write component**

`piece.component.ts`:

```typescript
import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Piece } from '../../../../core/models';
import { getPieceColorClass } from '../../shared/pieces.utils';

@Component({
  selector: 'app-piece',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './piece.component.html',
  styleUrls: ['./piece.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PieceComponent {
  readonly piece = input.required<Piece>();
  readonly selected = input<boolean>(false);
  readonly mustUse = input<boolean>(false);
  readonly select = output<number>();

  colorClass(): string { return getPieceColorClass(this.piece()); }

  onTap(): void { this.select.emit(this.piece().id); }
}
```

`piece.component.html`:

```html
<div class="piece" [class.selected]="selected()" [class.must-use]="mustUse()"
     [class]="colorClass()" (click)="onTap()"
     [attr.data-piece-id]="piece().id"
     [attr.aria-label]="piece().isJoker ? 'Joker' : (piece().color + ' ' + piece().num)">
  <span *ngIf="!piece().isJoker" class="num">{{ piece().num }}</span>
  <span *ngIf="piece().isJoker" class="joker-star">★</span>
</div>
```

`piece.component.scss`:

```scss
:host { display: inline-block; }

.piece {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 64px;
  border-radius: 6px;
  font-family: 'Playfair Display', serif;
  font-weight: 700;
  font-size: 22px;
  color: white;
  cursor: pointer;
  user-select: none;
  transition: transform 150ms ease-out, box-shadow 150ms;
  border: 2px solid transparent;
  background: #333;

  &.color-red    { background: linear-gradient(180deg, #d4554a, #a13a2f); }
  &.color-yellow { background: linear-gradient(180deg, #e8c050, #b08020); color: #2a1500; }
  &.color-blue   { background: linear-gradient(180deg, #4a7fd4, #2f549f); }
  &.color-black  { background: linear-gradient(180deg, #444, #1a1a1a); }
  &.color-joker  { background: linear-gradient(135deg, #d4554a, #e8c050, #4a7fd4, #444); }

  &.selected {
    transform: translateY(-8px);
    box-shadow: 0 0 0 2px #ffc409;
  }

  &.must-use {
    border-color: #eb445a;
    animation: must-pulse 1.5s ease-in-out infinite;
  }

  .num { line-height: 1; }
  .joker-star { font-size: 28px; }
}

@keyframes must-pulse {
  0%, 100% { box-shadow: 0 0 0 2px #eb445a; }
  50% { box-shadow: 0 0 0 4px #eb445a; }
}
```

- [ ] **Step 2: Write test**

`piece.component.spec.ts`:

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PieceComponent } from './piece.component';
import { TEST_PIECES } from '../../../../../test-utils/game-test-data';

describe('PieceComponent', () => {
  let fixture: ComponentFixture<PieceComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [PieceComponent] });
    fixture = TestBed.createComponent(PieceComponent);
  });

  it('renders num for non-joker', () => {
    fixture.componentRef.setInput('piece', TEST_PIECES['red7']);
    fixture.detectChanges();
    const numEl = fixture.nativeElement.querySelector('.num');
    expect(numEl?.textContent.trim()).toBe('7');
  });

  it('renders star for joker', () => {
    fixture.componentRef.setInput('piece', TEST_PIECES['joker1']);
    fixture.detectChanges();
    const starEl = fixture.nativeElement.querySelector('.joker-star');
    expect(starEl?.textContent.trim()).toBe('★');
  });

  it('applies color class', () => {
    fixture.componentRef.setInput('piece', TEST_PIECES['blue7']);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.piece').classList).toContain('color-blue');
  });

  it('applies selected class when selected=true', () => {
    fixture.componentRef.setInput('piece', TEST_PIECES['red5']);
    fixture.componentRef.setInput('selected', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.piece').classList).toContain('selected');
  });

  it('applies must-use class', () => {
    fixture.componentRef.setInput('piece', TEST_PIECES['red5']);
    fixture.componentRef.setInput('mustUse', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.piece').classList).toContain('must-use');
  });

  it('emits select with piece id on click', () => {
    fixture.componentRef.setInput('piece', TEST_PIECES['red5']);
    fixture.detectChanges();
    let emitted: number | undefined;
    fixture.componentInstance.select.subscribe(id => emitted = id);
    fixture.nativeElement.querySelector('.piece').click();
    expect(emitted).toBe(TEST_PIECES['red5'].id);
  });
});
```

- [ ] **Step 3: Run + commit**

```bash
npx ng test --watch=false --browsers=ChromeHeadless --include='**/piece.component.spec.ts'
git add src/app/features/game/components/piece/
git commit -m "feat(game): PieceComponent (atomic visual with color/selected/must-use states) + tests"
```

---

### Task C2: `AtuDisplayComponent` + tests

**Files:**
- Create: `frontend/src/app/features/game/components/atu-display/atu-display.component.{ts,html,scss,spec.ts}`

- [ ] **Step 1: Component**

`atu-display.component.ts`:

```typescript
import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Piece } from '../../../../core/models';
import { PieceComponent } from '../piece/piece.component';

@Component({
  selector: 'app-atu-display',
  standalone: true,
  imports: [CommonModule, PieceComponent],
  templateUrl: './atu-display.component.html',
  styleUrls: ['./atu-display.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AtuDisplayComponent {
  readonly atu = input.required<Piece>();

  readonly doubleGame = computed(() => {
    const a = this.atu();
    return a.isJoker || a.num === 1;
  });
}
```

`atu-display.component.html`:

```html
<div class="atu" [class.double-game]="doubleGame()">
  <span class="label">Atu</span>
  <app-piece [piece]="atu()"></app-piece>
  <span *ngIf="doubleGame()" class="double-badge">JOC DUBLU</span>
</div>
```

`atu-display.component.scss`:

```scss
.atu {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px;

  .label { font-size: 10px; text-transform: uppercase; opacity: 0.7; letter-spacing: 0.05em; }
  .double-badge {
    background: var(--ion-color-warning, #ffc409);
    color: black;
    font-size: 9px;
    font-weight: 700;
    padding: 2px 6px;
    border-radius: 4px;
    letter-spacing: 0.05em;
  }
}
```

- [ ] **Step 2: Test**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AtuDisplayComponent } from './atu-display.component';
import { TEST_PIECES } from '../../../../../test-utils/game-test-data';

describe('AtuDisplayComponent', () => {
  let fixture: ComponentFixture<AtuDisplayComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [AtuDisplayComponent] });
    fixture = TestBed.createComponent(AtuDisplayComponent);
  });

  it('shows piece + Atu label', () => {
    fixture.componentRef.setInput('atu', TEST_PIECES['red5']);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.label')?.textContent.trim()).toBe('Atu');
    expect(fixture.nativeElement.querySelector('app-piece')).toBeTruthy();
  });

  it('shows JOC DUBLU when atu is joker', () => {
    fixture.componentRef.setInput('atu', TEST_PIECES['joker1']);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.double-badge')?.textContent.trim()).toBe('JOC DUBLU');
  });

  it('shows JOC DUBLU when atu is 1', () => {
    fixture.componentRef.setInput('atu', TEST_PIECES['red1']);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.double-badge')).toBeTruthy();
  });

  it('hides badge for non-double atu', () => {
    fixture.componentRef.setInput('atu', TEST_PIECES['red5']);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.double-badge')).toBeNull();
  });
});
```

- [ ] **Step 3: Run + commit**

```bash
npx ng test --watch=false --browsers=ChromeHeadless --include='**/atu-display.component.spec.ts'
git add src/app/features/game/components/atu-display/
git commit -m "feat(game): AtuDisplayComponent (with JOC DUBLU badge for joker/1) + tests"
```

---

## Phase D — Hand (with CDK Drag&Drop)

### Task D1: `HandComponent` + tests

**Files:**
- Create: `frontend/src/app/features/game/components/hand/hand.component.{ts,html,scss,spec.ts}`

- [ ] **Step 1: Component**

`hand.component.ts`:

```typescript
import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CdkDragDrop, CdkDrag, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { Piece } from '../../../../core/models';
import { PieceComponent } from '../piece/piece.component';

@Component({
  selector: 'app-hand',
  standalone: true,
  imports: [CommonModule, CdkDrag, CdkDropList, PieceComponent],
  templateUrl: './hand.component.html',
  styleUrls: ['./hand.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HandComponent {
  readonly pieces = input.required<Piece[]>();
  readonly selectedIds = input<Set<number>>(new Set<number>());
  readonly mustUsePieceId = input<number | null>(null);

  readonly pieceClicked = output<number>();
  readonly reorder = output<{from: number; to: number}>();

  // For CdkDropList to know which lists can drop here (the hand) — set externally by parent
  readonly connectedTo = input<string[]>([]);

  // List ID used by other lists (table-zone, meld-card) to drop pieces into the hand if ever needed (not used in 4b)
  readonly listId = 'hand-list';

  isSelected(piece: Piece): boolean { return this.selectedIds().has(piece.id); }
  isMustUse(piece: Piece): boolean { return this.mustUsePieceId() === piece.id; }

  onDrop(event: CdkDragDrop<Piece[]>): void {
    if (event.previousContainer === event.container) {
      // Reorder within the hand
      this.reorder.emit({ from: event.previousIndex, to: event.currentIndex });
    }
    // Drops INTO other lists (discard, meld) are handled by THOSE lists' (cdkDropListDropped) events
  }
}
```

`hand.component.html`:

```html
<div class="hand"
     cdkDropList
     [cdkDropListOrientation]="'horizontal'"
     [id]="listId"
     [cdkDropListConnectedTo]="connectedTo()"
     (cdkDropListDropped)="onDrop($event)">
  <div class="piece-wrap" *ngFor="let piece of pieces(); trackBy: trackById" cdkDrag>
    <app-piece [piece]="piece"
               [selected]="isSelected(piece)"
               [mustUse]="isMustUse(piece)"
               (select)="pieceClicked.emit($event)">
    </app-piece>
  </div>
</div>
```

Add the `trackById` method in `hand.component.ts`:

```typescript
  trackById(_index: number, piece: Piece): number { return piece.id; }
```

`hand.component.scss`:

```scss
.hand {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px;
  min-height: 80px;
  background: rgba(255,255,255,0.04);
  border-radius: 12px;
}
.piece-wrap { display: inline-block; }
.cdk-drag-preview { opacity: 0.9; }
.cdk-drag-placeholder { opacity: 0.3; }
.cdk-drop-list-dragging .piece-wrap:not(.cdk-drag-placeholder) {
  transition: transform 150ms;
}
```

- [ ] **Step 2: Test**

`hand.component.spec.ts`:

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HandComponent } from './hand.component';
import { TEST_PIECES } from '../../../../../test-utils/game-test-data';

describe('HandComponent', () => {
  let fixture: ComponentFixture<HandComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HandComponent] });
    fixture = TestBed.createComponent(HandComponent);
  });

  it('renders one <app-piece> per piece', () => {
    fixture.componentRef.setInput('pieces', [TEST_PIECES['red5'], TEST_PIECES['red6'], TEST_PIECES['red7']]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('app-piece').length).toBe(3);
  });

  it('passes selected=true for ids in selectedIds', () => {
    fixture.componentRef.setInput('pieces', [TEST_PIECES['red5'], TEST_PIECES['red6']]);
    fixture.componentRef.setInput('selectedIds', new Set([TEST_PIECES['red5'].id]));
    fixture.detectChanges();
    const pieces = fixture.nativeElement.querySelectorAll('.piece');
    expect(pieces[0].classList.contains('selected')).toBeTrue();
    expect(pieces[1].classList.contains('selected')).toBeFalse();
  });

  it('passes mustUse=true for mustUsePieceId match', () => {
    fixture.componentRef.setInput('pieces', [TEST_PIECES['red5'], TEST_PIECES['red6']]);
    fixture.componentRef.setInput('mustUsePieceId', TEST_PIECES['red5'].id);
    fixture.detectChanges();
    const pieces = fixture.nativeElement.querySelectorAll('.piece');
    expect(pieces[0].classList.contains('must-use')).toBeTrue();
    expect(pieces[1].classList.contains('must-use')).toBeFalse();
  });

  it('emits pieceClicked when a piece is clicked', () => {
    fixture.componentRef.setInput('pieces', [TEST_PIECES['red5']]);
    fixture.detectChanges();
    let emitted: number | undefined;
    fixture.componentInstance.pieceClicked.subscribe(id => emitted = id);
    fixture.nativeElement.querySelector('.piece').click();
    expect(emitted).toBe(TEST_PIECES['red5'].id);
  });
});
```

(CDK drag-drop reorder behavior is harder to test purely unit; covered by smoke test.)

- [ ] **Step 3: Run + commit**

```bash
npx ng test --watch=false --browsers=ChromeHeadless --include='**/hand.component.spec.ts'
git add src/app/features/game/components/hand/
git commit -m "feat(game): HandComponent — CDK drop list, pass-through to PieceComponent + tests"
```

---

## End of Part 1

Continuă cu Part 2 (`2026-05-17-stage4b-game-ui-part2.md`) — Phases E (table area), F (opponents), G (timer + action-bar + modal), H (animations), I (GamePage), J (smoke + README).
