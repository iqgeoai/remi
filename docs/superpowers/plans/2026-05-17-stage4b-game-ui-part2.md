# Stage 4b — Game UI Implementation Plan (Part 2: Table + Opponents + Timer/Action/Modal + Animations + GamePage)

> **For agentic workers:** Continuation of Part 1. Same TDD discipline.

**Pre-requisite:** Part 1 (A+B+C+D) — `@angular/cdk` installed, pieces.utils, meld-detection, game-test-data, PieceComponent, AtuDisplayComponent, HandComponent.

---

## Phase E — Table area

### Task E1: `StockPileComponent` + tests

**Files:**
- Create: `frontend/src/app/features/game/components/stock-pile/stock-pile.component.{ts,html,scss,spec.ts}`

- [ ] **Step 1: Component**

`stock-pile.component.ts`:

```typescript
import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-stock-pile',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './stock-pile.component.html',
  styleUrls: ['./stock-pile.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StockPileComponent {
  readonly count = input.required<number>();
  readonly canDraw = input<boolean>(false);
  readonly clicked = output<void>();

  onClick(): void {
    if (this.canDraw()) this.clicked.emit();
  }
}
```

`stock-pile.component.html`:

```html
<button class="stock" [class.can-draw]="canDraw()" [disabled]="!canDraw()" (click)="onClick()"
        [attr.aria-label]="'Pachet (' + count() + ' rămase)'">
  <span class="count">{{ count() }}</span>
  <span class="label">pachet</span>
</button>
```

`stock-pile.component.scss`:

```scss
.stock {
  width: 60px;
  height: 80px;
  border-radius: 8px;
  background: linear-gradient(135deg, #4a3520, #2a1f12);
  border: 2px solid #6a4a2a;
  color: #f4e8d0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: not-allowed;
  opacity: 0.5;
  font-family: 'Inter', sans-serif;

  &.can-draw {
    cursor: pointer;
    opacity: 1;
    box-shadow: 0 0 12px rgba(255, 196, 9, 0.4);
  }
  &.can-draw:active { transform: scale(0.95); }

  .count { font-size: 22px; font-weight: 700; }
  .label { font-size: 10px; text-transform: uppercase; opacity: 0.7; }
}
```

- [ ] **Step 2: Test**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { StockPileComponent } from './stock-pile.component';

describe('StockPileComponent', () => {
  let fixture: ComponentFixture<StockPileComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [StockPileComponent] });
    fixture = TestBed.createComponent(StockPileComponent);
  });

  it('renders count', () => {
    fixture.componentRef.setInput('count', 42);
    fixture.componentRef.setInput('canDraw', false);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.count').textContent.trim()).toBe('42');
  });

  it('button disabled when canDraw=false', () => {
    fixture.componentRef.setInput('count', 10);
    fixture.componentRef.setInput('canDraw', false);
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('.stock');
    expect(btn.disabled).toBeTrue();
  });

  it('emits clicked when canDraw=true and clicked', () => {
    fixture.componentRef.setInput('count', 10);
    fixture.componentRef.setInput('canDraw', true);
    fixture.detectChanges();
    let emitted = false;
    fixture.componentInstance.clicked.subscribe(() => emitted = true);
    fixture.nativeElement.querySelector('.stock').click();
    expect(emitted).toBeTrue();
  });

  it('does not emit when canDraw=false and clicked', () => {
    fixture.componentRef.setInput('count', 10);
    fixture.componentRef.setInput('canDraw', false);
    fixture.detectChanges();
    let emitted = false;
    fixture.componentInstance.clicked.subscribe(() => emitted = true);
    fixture.nativeElement.querySelector('.stock').click();
    expect(emitted).toBeFalse();
  });
});
```

- [ ] **Step 3: Run + commit**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npx ng test --watch=false --browsers=ChromeHeadless --include='**/stock-pile.component.spec.ts'
git add src/app/features/game/components/stock-pile/
git commit -m "feat(game): StockPileComponent — count + canDraw gate + click emit + tests"
```

---

### Task E2: `DiscardPileComponent` + tests

**Files:**
- Create: `frontend/src/app/features/game/components/discard-pile/discard-pile.component.{ts,html,scss,spec.ts}`

- [ ] **Step 1: Component**

`discard-pile.component.ts`:

```typescript
import { Component, ChangeDetectionStrategy, input, output, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CdkDropList, CdkDragDrop } from '@angular/cdk/drag-drop';
import { Piece } from '../../../../core/models';
import { PieceComponent } from '../piece/piece.component';

@Component({
  selector: 'app-discard-pile',
  standalone: true,
  imports: [CommonModule, CdkDropList, PieceComponent],
  templateUrl: './discard-pile.component.html',
  styleUrls: ['./discard-pile.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DiscardPileComponent {
  readonly discard = input.required<Piece[]>();      // last = top
  readonly canTake = input<boolean>(false);
  readonly takeRequested = output<number>();          // emits index taken (default top)
  readonly pieceDroppedHere = output<number>();       // emits pieceId dropped from hand (Discard action)

  readonly topPiece = computed<Piece | null>(() => {
    const d = this.discard();
    return d.length > 0 ? d[d.length - 1] : null;
  });

  readonly listId = 'discard-list';

  onTopClick(): void {
    if (!this.canTake()) return;
    this.takeRequested.emit(this.discard().length - 1);
  }

  onDrop(event: CdkDragDrop<unknown>): void {
    // Only handle if a piece was dropped from another list (the hand)
    if (event.previousContainer === event.container) return;
    const draggedPiece = event.item.data as Piece;
    if (draggedPiece && typeof draggedPiece.id === 'number') {
      this.pieceDroppedHere.emit(draggedPiece.id);
    }
  }
}
```

`discard-pile.component.html`:

```html
<div class="discard"
     cdkDropList
     [id]="listId"
     (cdkDropListDropped)="onDrop($event)">
  <ng-container *ngIf="topPiece() as top; else empty">
    <button class="top-clickable" [class.can-take]="canTake()" [disabled]="!canTake()" (click)="onTopClick()"
            aria-label="Ia piesa de sus din aruncate">
      <app-piece [piece]="top"></app-piece>
    </button>
    <span class="count" *ngIf="discard().length > 1">+{{ discard().length - 1 }}</span>
  </ng-container>
  <ng-template #empty><div class="empty">aruncate</div></ng-template>
</div>
```

`discard-pile.component.scss`:

```scss
.discard {
  width: 60px;
  min-height: 80px;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  padding: 4px;
  border-radius: 8px;
  background: rgba(0,0,0,0.2);
  border: 2px dashed rgba(255,255,255,0.15);

  .top-clickable {
    background: none; border: none; padding: 0; cursor: not-allowed;
    &.can-take { cursor: pointer; }
  }

  .count {
    font-size: 10px;
    color: rgba(255,255,255,0.6);
    margin-top: 2px;
  }

  .empty {
    color: rgba(255,255,255,0.4);
    font-size: 11px;
    text-transform: uppercase;
    padding: 16px 4px;
    text-align: center;
  }
}

:host-context(.cdk-drop-list-receiving) .discard {
  border-color: #ffc409;
  background: rgba(255, 196, 9, 0.1);
}
```

- [ ] **Step 2: Test**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DiscardPileComponent } from './discard-pile.component';
import { TEST_PIECES } from '../../../../../test-utils/game-test-data';

describe('DiscardPileComponent', () => {
  let fixture: ComponentFixture<DiscardPileComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [DiscardPileComponent] });
    fixture = TestBed.createComponent(DiscardPileComponent);
  });

  it('renders empty state when discard is empty', () => {
    fixture.componentRef.setInput('discard', []);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.empty')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-piece')).toBeNull();
  });

  it('renders top piece when discard non-empty', () => {
    fixture.componentRef.setInput('discard', [TEST_PIECES['red1'], TEST_PIECES['red7']]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-piece')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.num')?.textContent.trim()).toBe('7');
  });

  it('shows +N when discard has more than 1', () => {
    fixture.componentRef.setInput('discard', [TEST_PIECES['red1'], TEST_PIECES['red7'], TEST_PIECES['blue7']]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.count')?.textContent.trim()).toBe('+2');
  });

  it('emits takeRequested with top idx when canTake and clicked', () => {
    fixture.componentRef.setInput('discard', [TEST_PIECES['red1'], TEST_PIECES['red7']]);
    fixture.componentRef.setInput('canTake', true);
    fixture.detectChanges();
    let emitted: number | undefined;
    fixture.componentInstance.takeRequested.subscribe(idx => emitted = idx);
    fixture.nativeElement.querySelector('.top-clickable').click();
    expect(emitted).toBe(1);   // top idx
  });

  it('does not emit when !canTake', () => {
    fixture.componentRef.setInput('discard', [TEST_PIECES['red7']]);
    fixture.componentRef.setInput('canTake', false);
    fixture.detectChanges();
    let emitted = false;
    fixture.componentInstance.takeRequested.subscribe(() => emitted = true);
    fixture.nativeElement.querySelector('.top-clickable').click();
    expect(emitted).toBeFalse();
  });
});
```

- [ ] **Step 3: Run + commit**

```bash
npx ng test --watch=false --browsers=ChromeHeadless --include='**/discard-pile.component.spec.ts'
git add src/app/features/game/components/discard-pile/
git commit -m "feat(game): DiscardPileComponent — top + count + take + drop target + tests"
```

---

### Task E3: `MeldCardComponent` + tests

**Files:**
- Create: `frontend/src/app/features/game/components/meld-card/meld-card.component.{ts,html,scss,spec.ts}`

- [ ] **Step 1: Component**

`meld-card.component.ts`:

```typescript
import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CdkDropList, CdkDragDrop } from '@angular/cdk/drag-drop';
import { Meld, Piece } from '../../../../core/models';
import { PieceComponent } from '../piece/piece.component';

@Component({
  selector: 'app-meld-card',
  standalone: true,
  imports: [CommonModule, CdkDropList, PieceComponent],
  templateUrl: './meld-card.component.html',
  styleUrls: ['./meld-card.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MeldCardComponent {
  readonly meld = input.required<Meld>();
  readonly meldIdx = input.required<number>();
  readonly layoffDropped = output<{pieceId: number; meldIdx: number}>();

  get listId(): string { return 'meld-list-' + this.meldIdx(); }

  onDrop(event: CdkDragDrop<unknown>): void {
    if (event.previousContainer === event.container) return;
    const draggedPiece = event.item.data as Piece;
    if (draggedPiece && typeof draggedPiece.id === 'number') {
      this.layoffDropped.emit({ pieceId: draggedPiece.id, meldIdx: this.meldIdx() });
    }
  }
}
```

`meld-card.component.html`:

```html
<div class="meld" [attr.data-type]="meld().type"
     cdkDropList
     [id]="listId"
     (cdkDropListDropped)="onDrop($event)">
  <span class="type-label">{{ meld().type === 'GROUP' ? 'Grup' : 'Suită' }}</span>
  <div class="pieces">
    <app-piece *ngFor="let piece of meld().pieces" [piece]="piece"></app-piece>
  </div>
</div>
```

`meld-card.component.scss`:

```scss
.meld {
  display: inline-flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px 8px;
  border-radius: 8px;
  background: rgba(255,255,255,0.05);
  border: 2px solid transparent;

  .type-label {
    font-size: 9px;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    opacity: 0.65;
  }
  .pieces { display: flex; gap: 4px; }
}

:host-context(.cdk-drop-list-receiving) .meld {
  border-color: #2dd36f;
  background: rgba(45, 211, 111, 0.1);
}
```

- [ ] **Step 2: Test**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MeldCardComponent } from './meld-card.component';
import { TEST_PIECES } from '../../../../../test-utils/game-test-data';
import { Meld } from '../../../../core/models';

describe('MeldCardComponent', () => {
  let fixture: ComponentFixture<MeldCardComponent>;

  const groupMeld: Meld = {
    owner: 0, type: 'GROUP',
    pieces: [TEST_PIECES['yel10'], TEST_PIECES['blu10'], TEST_PIECES['blk10']],
    placedBy: { 9: 0, 10: 0, 11: 0 },
  };

  const suiteMeld: Meld = {
    owner: 0, type: 'SUITE',
    pieces: [TEST_PIECES['red5'], TEST_PIECES['red6'], TEST_PIECES['red7']],
    placedBy: { 2: 0, 3: 0, 4: 0 },
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [MeldCardComponent] });
    fixture = TestBed.createComponent(MeldCardComponent);
  });

  it('renders GROUP label', () => {
    fixture.componentRef.setInput('meld', groupMeld);
    fixture.componentRef.setInput('meldIdx', 0);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.type-label').textContent.trim()).toBe('Grup');
  });

  it('renders SUITE label', () => {
    fixture.componentRef.setInput('meld', suiteMeld);
    fixture.componentRef.setInput('meldIdx', 1);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.type-label').textContent.trim()).toBe('Suită');
  });

  it('renders all pieces', () => {
    fixture.componentRef.setInput('meld', groupMeld);
    fixture.componentRef.setInput('meldIdx', 0);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('app-piece').length).toBe(3);
  });
});
```

(Drop event tests are skipped — CDK drop simulation is complex and covered by smoke test.)

- [ ] **Step 3: Run + commit**

```bash
npx ng test --watch=false --browsers=ChromeHeadless --include='**/meld-card.component.spec.ts'
git add src/app/features/game/components/meld-card/
git commit -m "feat(game): MeldCardComponent — pieces + type label + drop target for layoff + tests"
```

---

### Task E4: `MeldsAreaComponent` (no test)

**Files:**
- Create: `frontend/src/app/features/game/components/melds-area/melds-area.component.{ts,html,scss}`

- [ ] **Step 1: Component**

`melds-area.component.ts`:

```typescript
import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Meld } from '../../../../core/models';
import { MeldCardComponent } from '../meld-card/meld-card.component';

@Component({
  selector: 'app-melds-area',
  standalone: true,
  imports: [CommonModule, MeldCardComponent],
  templateUrl: './melds-area.component.html',
  styleUrls: ['./melds-area.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MeldsAreaComponent {
  readonly melds = input.required<Meld[]>();
  readonly layoffDropped = output<{pieceId: number; meldIdx: number}>();
}
```

`melds-area.component.html`:

```html
<div class="melds-area">
  <p *ngIf="melds().length === 0" class="empty-hint">Nicio etalare încă.</p>
  <app-meld-card *ngFor="let meld of melds(); let i = index"
                 [meld]="meld" [meldIdx]="i"
                 (layoffDropped)="layoffDropped.emit($event)">
  </app-meld-card>
</div>
```

`melds-area.component.scss`:

```scss
.melds-area {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px;
  min-height: 80px;
  .empty-hint { font-size: 12px; opacity: 0.5; font-style: italic; }
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npx ng build --configuration=development
git add src/app/features/game/components/melds-area/
git commit -m "feat(game): MeldsAreaComponent — list of MeldCards + empty hint"
```

---

### Task E5: `TableZoneComponent` (composer, no test)

**Files:**
- Create: `frontend/src/app/features/game/components/table-zone/table-zone.component.{ts,html,scss}`

- [ ] **Step 1: Component**

`table-zone.component.ts`:

```typescript
import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Piece, Meld } from '../../../../core/models';
import { AtuDisplayComponent } from '../atu-display/atu-display.component';
import { StockPileComponent } from '../stock-pile/stock-pile.component';
import { DiscardPileComponent } from '../discard-pile/discard-pile.component';
import { MeldsAreaComponent } from '../melds-area/melds-area.component';

@Component({
  selector: 'app-table-zone',
  standalone: true,
  imports: [CommonModule, AtuDisplayComponent, StockPileComponent, DiscardPileComponent, MeldsAreaComponent],
  templateUrl: './table-zone.component.html',
  styleUrls: ['./table-zone.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TableZoneComponent {
  readonly atu = input.required<Piece>();
  readonly stockCount = input.required<number>();
  readonly discard = input.required<Piece[]>();
  readonly melds = input.required<Meld[]>();
  readonly canDraw = input<boolean>(false);
  readonly canTake = input<boolean>(false);

  readonly drawClicked = output<void>();
  readonly takeRequested = output<number>();
  readonly pieceDroppedToDiscard = output<number>();
  readonly layoffDropped = output<{pieceId: number; meldIdx: number}>();
}
```

`table-zone.component.html`:

```html
<div class="table">
  <div class="top-row">
    <app-atu-display [atu]="atu()"></app-atu-display>
    <app-stock-pile [count]="stockCount()" [canDraw]="canDraw()" (clicked)="drawClicked.emit()"></app-stock-pile>
    <app-discard-pile [discard]="discard()" [canTake]="canTake()"
                      (takeRequested)="takeRequested.emit($event)"
                      (pieceDroppedHere)="pieceDroppedToDiscard.emit($event)"></app-discard-pile>
  </div>
  <app-melds-area [melds]="melds()" (layoffDropped)="layoffDropped.emit($event)"></app-melds-area>
</div>
```

`table-zone.component.scss`:

```scss
.table {
  background: linear-gradient(180deg, rgba(0,0,0,0.25), rgba(0,0,0,0.05));
  border-top: 1px solid rgba(255,255,255,0.06);
  border-bottom: 1px solid rgba(255,255,255,0.06);
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.top-row {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  flex-wrap: wrap;
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npx ng build --configuration=development
git add src/app/features/game/components/table-zone/
git commit -m "feat(game): TableZoneComponent — composes atu, stock, discard, melds area"
```

---

## Phase F — Opponents

### Task F1: `OpponentSummaryComponent` + tests

**Files:**
- Create: `frontend/src/app/features/game/components/opponent-summary/opponent-summary.component.{ts,html,scss,spec.ts}`

- [ ] **Step 1: Component**

`opponent-summary.component.ts`:

```typescript
import { Component, ChangeDetectionStrategy, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PlayerView } from '../../../../core/models';

@Component({
  selector: 'app-opponent-summary',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './opponent-summary.component.html',
  styleUrls: ['./opponent-summary.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OpponentSummaryComponent {
  readonly player = input.required<PlayerView>();
  readonly active = input<boolean>(false);
  readonly score = input<number>(0);

  scoreLabel(): string {
    const s = this.score();
    return s > 0 ? `+${s}` : `${s}`;
  }
}
```

`opponent-summary.component.html`:

```html
<div class="opp" [class.active]="active()" [class.etalat]="player().hasEtalat">
  <div class="name">{{ player().name }}</div>
  <div class="stats">
    <span class="pieces">{{ player().handCount }} pcs</span>
    <span class="score">{{ scoreLabel() }}</span>
  </div>
</div>
```

`opponent-summary.component.scss`:

```scss
.opp {
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 12px;
  padding: 8px 10px;
  min-width: 80px;
  flex: 1;
  position: relative;

  &.active {
    border-color: #d4a443;
    box-shadow: 0 0 0 2px rgba(212,164,67,0.2);
  }
  &.etalat::after {
    content: '●';
    position: absolute;
    top: 4px; right: 8px;
    color: #6cbf6c;
    font-size: 10px;
  }

  .name { font-size: 11px; opacity: 0.7; text-transform: uppercase; letter-spacing: 0.05em; }
  .stats { display: flex; justify-content: space-between; margin-top: 2px; }
  .pieces { font-size: 14px; font-weight: 600; }
  .score { font-family: 'Playfair Display', serif; font-size: 16px; font-weight: 700; color: #d4a443; }
}
```

- [ ] **Step 2: Test**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OpponentSummaryComponent } from './opponent-summary.component';
import { TEST_PLAYER_BOB } from '../../../../../test-utils/game-test-data';

describe('OpponentSummaryComponent', () => {
  let fixture: ComponentFixture<OpponentSummaryComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [OpponentSummaryComponent] });
    fixture = TestBed.createComponent(OpponentSummaryComponent);
  });

  it('renders name, handCount, score', () => {
    fixture.componentRef.setInput('player', TEST_PLAYER_BOB);
    fixture.componentRef.setInput('score', 50);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.name').textContent.trim()).toBe('Bob');
    expect(fixture.nativeElement.querySelector('.pieces').textContent.trim()).toBe('14 pcs');
    expect(fixture.nativeElement.querySelector('.score').textContent.trim()).toBe('+50');
  });

  it('formats negative score without extra plus', () => {
    fixture.componentRef.setInput('player', TEST_PLAYER_BOB);
    fixture.componentRef.setInput('score', -30);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.score').textContent.trim()).toBe('-30');
  });

  it('applies active class when active=true', () => {
    fixture.componentRef.setInput('player', TEST_PLAYER_BOB);
    fixture.componentRef.setInput('active', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.opp').classList).toContain('active');
  });

  it('applies etalat class when player.hasEtalat=true', () => {
    fixture.componentRef.setInput('player', { ...TEST_PLAYER_BOB, hasEtalat: true });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.opp').classList).toContain('etalat');
  });
});
```

- [ ] **Step 3: Run + commit**

```bash
npx ng test --watch=false --browsers=ChromeHeadless --include='**/opponent-summary.component.spec.ts'
git add src/app/features/game/components/opponent-summary/
git commit -m "feat(game): OpponentSummaryComponent — name/handCount/score/active/etalat + tests"
```

---

### Task F2: `OpponentsBarComponent` + tests

**Files:**
- Create: `frontend/src/app/features/game/components/opponents-bar/opponents-bar.component.{ts,html,scss,spec.ts}`

- [ ] **Step 1: Component**

`opponents-bar.component.ts`:

```typescript
import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PlayerView } from '../../../../core/models';
import { OpponentSummaryComponent } from '../opponent-summary/opponent-summary.component';

interface OpponentRow {
  player: PlayerView;
  globalIdx: number;
  score: number;
}

@Component({
  selector: 'app-opponents-bar',
  standalone: true,
  imports: [CommonModule, OpponentSummaryComponent],
  templateUrl: './opponents-bar.component.html',
  styleUrls: ['./opponents-bar.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OpponentsBarComponent {
  readonly players = input.required<PlayerView[]>();
  readonly currentIdx = input.required<number>();
  readonly mySeatIdx = input.required<number>();
  readonly totals = input.required<number[]>();

  readonly opponents = computed<OpponentRow[]>(() => {
    const me = this.mySeatIdx();
    return this.players()
      .map((player, idx) => ({ player, globalIdx: idx, score: this.totals()[idx] ?? 0 }))
      .filter(row => row.globalIdx !== me);
  });
}
```

`opponents-bar.component.html`:

```html
<div class="opponents">
  <app-opponent-summary *ngFor="let row of opponents()"
                       [player]="row.player"
                       [score]="row.score"
                       [active]="row.globalIdx === currentIdx()">
  </app-opponent-summary>
</div>
```

`opponents-bar.component.scss`:

```scss
.opponents {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  overflow-x: auto;
}
```

- [ ] **Step 2: Test**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OpponentsBarComponent } from './opponents-bar.component';
import { TEST_PLAYER_ALICE, TEST_PLAYER_BOB } from '../../../../../test-utils/game-test-data';

describe('OpponentsBarComponent', () => {
  let fixture: ComponentFixture<OpponentsBarComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [OpponentsBarComponent] });
    fixture = TestBed.createComponent(OpponentsBarComponent);
  });

  it('filters out my own seat', () => {
    fixture.componentRef.setInput('players', [TEST_PLAYER_ALICE, TEST_PLAYER_BOB]);
    fixture.componentRef.setInput('currentIdx', 0);
    fixture.componentRef.setInput('mySeatIdx', 0);
    fixture.componentRef.setInput('totals', [0, 0]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('app-opponent-summary').length).toBe(1);
  });

  it('marks active opponent when currentIdx is theirs', () => {
    fixture.componentRef.setInput('players', [TEST_PLAYER_ALICE, TEST_PLAYER_BOB]);
    fixture.componentRef.setInput('currentIdx', 1);   // bob's turn
    fixture.componentRef.setInput('mySeatIdx', 0);
    fixture.componentRef.setInput('totals', [0, 0]);
    fixture.detectChanges();
    const opp = fixture.nativeElement.querySelector('app-opponent-summary .opp');
    expect(opp.classList).toContain('active');
  });

  it('passes per-player score', () => {
    fixture.componentRef.setInput('players', [TEST_PLAYER_ALICE, TEST_PLAYER_BOB]);
    fixture.componentRef.setInput('currentIdx', 0);
    fixture.componentRef.setInput('mySeatIdx', 0);
    fixture.componentRef.setInput('totals', [100, -50]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.score').textContent.trim()).toBe('-50');
  });
});
```

- [ ] **Step 3: Run + commit**

```bash
npx ng test --watch=false --browsers=ChromeHeadless --include='**/opponents-bar.component.spec.ts'
git add src/app/features/game/components/opponents-bar/
git commit -m "feat(game): OpponentsBarComponent — filter mySeat, mark active, pass scores + tests"
```

---

## Phase G — Timer + Action bar + Round-end modal

### Task G1: `TurnTimerComponent` + tests

**Files:**
- Create: `frontend/src/app/features/game/components/turn-timer/turn-timer.component.{ts,html,scss,spec.ts}`

- [ ] **Step 1: Component**

`turn-timer.component.ts`:

```typescript
import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-turn-timer',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './turn-timer.component.html',
  styleUrls: ['./turn-timer.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TurnTimerComponent {
  readonly secondsLeft = input.required<number>();
  readonly totalSeconds = input<number>(120);

  readonly circumference = 2 * Math.PI * 18;   // r=18
  readonly dashOffset = computed(() => {
    const ratio = Math.max(0, Math.min(1, this.secondsLeft() / this.totalSeconds()));
    return this.circumference * (1 - ratio);
  });
  readonly critical = computed(() => this.secondsLeft() <= 10);
  readonly warn = computed(() => this.secondsLeft() <= 30 && this.secondsLeft() > 10);
}
```

`turn-timer.component.html`:

```html
<div class="timer" [class.critical]="critical()" [class.warn]="warn()">
  <svg width="44" height="44" viewBox="0 0 44 44">
    <circle cx="22" cy="22" r="18" fill="none" stroke="rgba(255,255,255,0.15)" stroke-width="3"/>
    <circle cx="22" cy="22" r="18" fill="none" stroke="currentColor" stroke-width="3"
            [attr.stroke-dasharray]="circumference"
            [attr.stroke-dashoffset]="dashOffset()"
            stroke-linecap="round"
            transform="rotate(-90 22 22)"/>
  </svg>
  <span class="num">{{ secondsLeft() }}s</span>
</div>
```

`turn-timer.component.scss`:

```scss
.timer {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #6cbf6c;
  font-family: 'Inter', sans-serif;

  &.warn { color: #ffc409; }
  &.critical { color: #eb445a; animation: pulse 0.6s ease-in-out infinite; }

  .num { font-size: 14px; font-weight: 600; min-width: 36px; text-align: right; }
}

@keyframes pulse { 50% { opacity: 0.5; } }
```

- [ ] **Step 2: Test**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TurnTimerComponent } from './turn-timer.component';

describe('TurnTimerComponent', () => {
  let fixture: ComponentFixture<TurnTimerComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [TurnTimerComponent] });
    fixture = TestBed.createComponent(TurnTimerComponent);
  });

  it('renders num seconds', () => {
    fixture.componentRef.setInput('secondsLeft', 90);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.num').textContent.trim()).toBe('90s');
  });

  it('applies warn class when seconds <= 30 and > 10', () => {
    fixture.componentRef.setInput('secondsLeft', 25);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.timer').classList).toContain('warn');
    expect(fixture.nativeElement.querySelector('.timer').classList).not.toContain('critical');
  });

  it('applies critical class when seconds <= 10', () => {
    fixture.componentRef.setInput('secondsLeft', 5);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.timer').classList).toContain('critical');
  });

  it('stroke-dashoffset shrinks as secondsLeft decreases', () => {
    fixture.componentRef.setInput('secondsLeft', 60);
    fixture.componentRef.setInput('totalSeconds', 120);
    fixture.detectChanges();
    const circle = fixture.nativeElement.querySelectorAll('circle')[1];
    const expectedOffset = (2 * Math.PI * 18) * (1 - 60/120);
    // Allow small float tolerance
    expect(Math.abs(parseFloat(circle.getAttribute('stroke-dashoffset')) - expectedOffset)).toBeLessThan(0.01);
  });
});
```

- [ ] **Step 3: Run + commit**

```bash
npx ng test --watch=false --browsers=ChromeHeadless --include='**/turn-timer.component.spec.ts'
git add src/app/features/game/components/turn-timer/
git commit -m "feat(game): TurnTimerComponent — SVG ring + warn/critical states + tests"
```

---

### Task G2: `ActionBarComponent` + tests

**Files:**
- Create: `frontend/src/app/features/game/components/action-bar/action-bar.component.{ts,html,scss,spec.ts}`

- [ ] **Step 1: Component**

`action-bar.component.ts`:

```typescript
import { Component, ChangeDetectionStrategy, input, output, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonButton } from '@ionic/angular/standalone';
import { MeldProposal } from '../../../../core/models';

@Component({
  selector: 'app-action-bar',
  standalone: true,
  imports: [CommonModule, IonButton],
  templateUrl: './action-bar.component.html',
  styleUrls: ['./action-bar.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActionBarComponent {
  readonly canDraw = input<boolean>(false);
  readonly selectedCount = input<number>(0);
  readonly canEtalat = input<boolean>(false);
  readonly proposedMelds = input<MeldProposal[]>([]);
  readonly mustUseHint = input<boolean>(false);

  readonly drawClicked = output<void>();
  readonly addMeldClicked = output<void>();
  readonly etalatClicked = output<void>();
  readonly cancelClicked = output<void>();

  readonly hasProposed = computed(() => this.proposedMelds().length > 0);
  readonly hasSelection = computed(() => this.selectedCount() > 0);
  readonly proposedLabel = computed(() => `Etalează (${this.proposedMelds().length})`);
}
```

`action-bar.component.html`:

```html
<div class="action-bar">
  <ion-button *ngIf="canDraw()" expand="block" color="primary" (click)="drawClicked.emit()">
    Trage de la pachet
  </ion-button>

  <ion-button *ngIf="hasSelection() && canEtalat()" expand="block" color="success" (click)="addMeldClicked.emit()">
    + Adaugă meld
  </ion-button>

  <ion-button *ngIf="hasProposed()" expand="block" color="warning" (click)="etalatClicked.emit()">
    {{ proposedLabel() }}
  </ion-button>

  <ion-button *ngIf="hasSelection() || hasProposed()" expand="block" fill="clear" color="medium" (click)="cancelClicked.emit()">
    Anulează
  </ion-button>

  <p *ngIf="!canDraw() && !hasSelection() && !hasProposed() && mustUseHint()" class="hint">
    Trebuie să folosești piesa luată din șir.
  </p>
  <p *ngIf="!canDraw() && !hasSelection() && !hasProposed() && !mustUseHint()" class="hint">
    Trage o piesă pe pachetul de aruncare sau selectează pentru etalat.
  </p>
</div>
```

`action-bar.component.scss`:

```scss
.action-bar {
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  .hint {
    text-align: center;
    font-size: 12px;
    opacity: 0.6;
    margin: 4px 0;
  }
}
```

- [ ] **Step 2: Test**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActionBarComponent } from './action-bar.component';

describe('ActionBarComponent', () => {
  let fixture: ComponentFixture<ActionBarComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ActionBarComponent] });
    fixture = TestBed.createComponent(ActionBarComponent);
  });

  it('shows Trage button when canDraw=true', () => {
    fixture.componentRef.setInput('canDraw', true);
    fixture.detectChanges();
    const btns = fixture.nativeElement.querySelectorAll('ion-button');
    expect(Array.from(btns).some((b: any) => b.textContent.includes('Trage'))).toBeTrue();
  });

  it('shows Adaugă meld when selectedCount>0 and canEtalat=true', () => {
    fixture.componentRef.setInput('selectedCount', 3);
    fixture.componentRef.setInput('canEtalat', true);
    fixture.detectChanges();
    const btns = fixture.nativeElement.querySelectorAll('ion-button');
    expect(Array.from(btns).some((b: any) => b.textContent.includes('Adaugă meld'))).toBeTrue();
  });

  it('shows Etalează (N) when proposedMelds non-empty', () => {
    fixture.componentRef.setInput('proposedMelds', [
      { type: 'GROUP', pieceIds: [1,2,3] },
      { type: 'SUITE', pieceIds: [4,5,6] },
    ]);
    fixture.detectChanges();
    const btns = fixture.nativeElement.querySelectorAll('ion-button');
    expect(Array.from(btns).some((b: any) => b.textContent.includes('Etalează (2)'))).toBeTrue();
  });

  it('emits drawClicked', () => {
    fixture.componentRef.setInput('canDraw', true);
    fixture.detectChanges();
    let emitted = false;
    fixture.componentInstance.drawClicked.subscribe(() => emitted = true);
    const drawBtn = Array.from(fixture.nativeElement.querySelectorAll('ion-button'))
      .find((b: any) => b.textContent.includes('Trage')) as HTMLElement;
    drawBtn.click();
    expect(emitted).toBeTrue();
  });
});
```

- [ ] **Step 3: Run + commit**

```bash
npx ng test --watch=false --browsers=ChromeHeadless --include='**/action-bar.component.spec.ts'
git add src/app/features/game/components/action-bar/
git commit -m "feat(game): ActionBarComponent — context-sensitive buttons + hints + tests"
```

---

### Task G3: `RoundEndModalComponent` + tests

**Files:**
- Create: `frontend/src/app/features/game/components/round-end-modal/round-end-modal.component.{ts,html,scss,spec.ts}`

- [ ] **Step 1: Component**

`round-end-modal.component.ts`:

```typescript
import { Component, ChangeDetectionStrategy, input, output, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonContent, IonButton } from '@ionic/angular/standalone';

export interface RoundResult {
  playerIdx: number;
  name: string;
  base: number;
  melded: number;
  handCount: number;
}

@Component({
  selector: 'app-round-end-modal',
  standalone: true,
  imports: [CommonModule, IonContent, IonButton],
  templateUrl: './round-end-modal.component.html',
  styleUrls: ['./round-end-modal.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RoundEndModalComponent {
  readonly results = input.required<RoundResult[]>();
  readonly winnerName = input.required<string>();
  readonly closeRequested = output<void>();

  readonly winnerIdx = computed(() => {
    const r = this.results();
    let best = 0;
    for (let i = 1; i < r.length; i++) if (r[i].base > r[best].base) best = i;
    return best;
  });

  format(n: number): string { return n > 0 ? `+${n}` : `${n}`; }
}
```

`round-end-modal.component.html`:

```html
<ion-content class="ion-padding">
  <h2>Runda terminată — {{ winnerName() }} câștigă</h2>
  <table class="scores">
    <thead><tr><th>Jucător</th><th class="num">Rundă</th><th class="num">Total</th></tr></thead>
    <tbody>
      <tr *ngFor="let r of results(); let i = index" [class.win]="i === winnerIdx()">
        <td>{{ r.name }}</td>
        <td class="num">{{ format(r.base) }}</td>
        <td class="num">—</td>
      </tr>
    </tbody>
  </table>
  <ion-button expand="block" (click)="closeRequested.emit()">Înapoi la lobby</ion-button>
</ion-content>
```

`round-end-modal.component.scss`:

```scss
h2 { text-align: center; margin: 16px 0; }
.scores {
  width: 100%; border-collapse: collapse; margin: 16px 0;
  th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid rgba(255,255,255,0.1); }
  th.num, td.num { text-align: right; }
  tr.win { background: rgba(45,211,111,0.1); font-weight: 700; }
}
```

- [ ] **Step 2: Test**

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RoundEndModalComponent, RoundResult } from './round-end-modal.component';

describe('RoundEndModalComponent', () => {
  let fixture: ComponentFixture<RoundEndModalComponent>;

  const results: RoundResult[] = [
    { playerIdx: 0, name: 'Alice', base: 60,  melded: 2, handCount: 0 },
    { playerIdx: 1, name: 'Bob',   base: -20, melded: 1, handCount: 5 },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [RoundEndModalComponent] });
    fixture = TestBed.createComponent(RoundEndModalComponent);
  });

  it('renders all results', () => {
    fixture.componentRef.setInput('results', results);
    fixture.componentRef.setInput('winnerName', 'Alice');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(2);
  });

  it('highlights winner row', () => {
    fixture.componentRef.setInput('results', results);
    fixture.componentRef.setInput('winnerName', 'Alice');
    fixture.detectChanges();
    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows[0].classList.contains('win')).toBeTrue();   // Alice has higher base
    expect(rows[1].classList.contains('win')).toBeFalse();
  });

  it('formats negative score without extra plus', () => {
    fixture.componentRef.setInput('results', results);
    fixture.componentRef.setInput('winnerName', 'Alice');
    fixture.detectChanges();
    const cells = fixture.nativeElement.querySelectorAll('tbody td.num');
    // First row's "Rundă" cell
    expect(cells[0].textContent.trim()).toBe('+60');
    expect(cells[2].textContent.trim()).toBe('-20');
  });

  it('emits closeRequested on button click', () => {
    fixture.componentRef.setInput('results', results);
    fixture.componentRef.setInput('winnerName', 'Alice');
    fixture.detectChanges();
    let emitted = false;
    fixture.componentInstance.closeRequested.subscribe(() => emitted = true);
    fixture.nativeElement.querySelector('ion-button').click();
    expect(emitted).toBeTrue();
  });
});
```

- [ ] **Step 3: Run + commit**

```bash
npx ng test --watch=false --browsers=ChromeHeadless --include='**/round-end-modal.component.spec.ts'
git add src/app/features/game/components/round-end-modal/
git commit -m "feat(game): RoundEndModalComponent — results table + winner highlight + tests"
```

---

## Phase H — Animations

### Task H1: `game.animations.ts`

**Files:**
- Create: `frontend/src/app/features/game/animations/game.animations.ts`

- [ ] **Step 1: Write triggers**

```typescript
import { animate, state, style, transition, trigger } from '@angular/animations';

export const pieceSelectedAnim = trigger('pieceSelected', [
  state('selected',   style({ transform: 'translateY(-8px)' })),
  state('unselected', style({ transform: 'translateY(0)' })),
  transition('* <=> *', animate('150ms ease-out')),
]);

export const drawSlideAnim = trigger('drawSlide', [
  transition(':enter', [
    style({ transform: 'translate(-100px, -50px) rotate(-15deg)', opacity: 0 }),
    animate('300ms ease-out', style({ transform: 'translate(0,0) rotate(0)', opacity: 1 })),
  ]),
]);

export const discardSlideAnim = trigger('discardSlide', [
  transition(':enter', [
    style({ transform: 'translateY(-100px)', opacity: 0 }),
    animate('250ms ease-in', style({ transform: 'translateY(0)', opacity: 1 })),
  ]),
]);

export const opponentActiveAnim = trigger('opponentActive', [
  state('active',   style({ boxShadow: '0 0 0 2px rgba(212,164,67,0.4)' })),
  state('inactive', style({ boxShadow: '0 0 0 0 transparent' })),
  transition('* <=> *', animate('200ms ease-out')),
]);
```

- [ ] **Step 2: Add `provideAnimations` to app.config.ts**

Modify `frontend/src/app/app.config.ts`. Add import:

```typescript
import { provideAnimations } from '@angular/platform-browser/animations';
```

Add to `providers` array (next to `provideIonicAngular()`):

```typescript
provideAnimations(),
```

- [ ] **Step 3: Build + commit**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
npx ng build --configuration=development
git add src/app/features/game/animations/ src/app/app.config.ts
git commit -m "feat(game): animation triggers + provideAnimations wiring"
```

---

## Phase I — GamePage orchestrator

### Task I1: Replace `GameDebugPage` with `GamePage`

**Files:**
- Delete (or overwrite): `frontend/src/app/features/game/game-debug.page.{ts,html,scss}`
- Create: `frontend/src/app/features/game/game.page.{ts,html,scss}`
- Update: `frontend/src/app/app.routes.ts` (point to `game.page` instead of `game-debug.page`)

This task replaces the 4a placeholder with the real orchestrator. Largest task in 4b.

- [ ] **Step 1: Write `game.page.ts`**

```typescript
import { Component, OnInit, OnDestroy, inject, computed, effect, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { Actions, ofType } from '@ngrx/effects';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { IonContent, ToastController } from '@ionic/angular/standalone';
import { interval, takeWhile } from 'rxjs';

import { Game } from '../../store/game/game.actions';
import { selectGameView, selectGameEvents, selectGameError } from '../../store/game/game.selectors';
import { selectUser } from '../../store/auth/auth.selectors';

import { Action, MeldProposal, DomainEvent } from '../../core/models';
import { ErrorBannerComponent } from '../../shared/error-banner/error-banner.component';

import { OpponentsBarComponent } from './components/opponents-bar/opponents-bar.component';
import { TableZoneComponent } from './components/table-zone/table-zone.component';
import { HandComponent } from './components/hand/hand.component';
import { TurnTimerComponent } from './components/turn-timer/turn-timer.component';
import { ActionBarComponent } from './components/action-bar/action-bar.component';
import { RoundEndModalComponent, RoundResult } from './components/round-end-modal/round-end-modal.component';

import { detectMeld } from './shared/meld-detection';

@Component({
  selector: 'app-game',
  standalone: true,
  imports: [
    CommonModule, RouterLink,
    IonContent,
    ErrorBannerComponent,
    OpponentsBarComponent, TableZoneComponent, HandComponent,
    TurnTimerComponent, ActionBarComponent, RoundEndModalComponent,
  ],
  templateUrl: './game.page.html',
  styleUrls: ['./game.page.scss'],
})
export default class GamePage implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly toasts = inject(ToastController);
  private readonly actions$ = inject(Actions);

  readonly view = this.store.selectSignal(selectGameView);
  readonly events = this.store.selectSignal(selectGameEvents);
  readonly error = this.store.selectSignal(selectGameError);
  readonly user = this.store.selectSignal(selectUser);

  readonly mySeatIdx = computed(() => {
    const v = this.view(); const u = this.user();
    if (!v || !u) return -1;
    return v.players.findIndex(p => p.name === u.username);
  });
  readonly isMyTurn = computed(() => this.view()?.current === this.mySeatIdx());
  readonly myPlayer = computed(() => {
    const v = this.view(); const idx = this.mySeatIdx();
    return idx >= 0 && v ? v.players[idx] : null;
  });

  // UI state
  readonly selectedIds = signal<Set<number>>(new Set<number>());
  readonly proposedMelds = signal<MeldProposal[]>([]);
  readonly lastSubmittedMelds = signal<MeldProposal[]>([]);
  readonly secondsLeft = signal(120);

  // Derived flags
  readonly canDraw = computed(() => this.isMyTurn() && this.view()?.phase === 'DRAW');
  readonly canTake = computed(() =>
    this.isMyTurn() && this.view()?.phase === 'DRAW' && (this.view()?.discard.length ?? 0) > 0);
  readonly canEtalat = computed(() => {
    const hand = this.myPlayer()?.hand ?? [];
    return detectMeld(hand, this.selectedIds()) !== null;
  });
  readonly showRoundEnd = computed(() => this.view()?.closed === true);

  readonly mustUseHint = computed(() => this.myPlayer()?.mustUsePieceId != null);

  readonly handListConnectedTo = computed(() => {
    const meldIds = (this.view()?.melds ?? []).map((_, i) => 'meld-list-' + i);
    return ['discard-list', ...meldIds];
  });

  gameId = '';

  ngOnInit(): void {
    this.gameId = this.route.snapshot.paramMap.get('id') ?? '';
    this.store.dispatch(Game.subscribeToGame({ gameId: this.gameId }));
    this.store.dispatch(Game.subscribeToErrors());
    this.store.dispatch(Game.loadGameRequested({ gameId: this.gameId }));

    // Error → toast
    effect(() => {
      const err = this.error();
      if (err) {
        this.toasts.create({ message: err.message, duration: 4000, color: 'danger' })
          .then(t => t.present()).catch(() => {});
        // Rollback proposedMelds if ETALAT was in flight
        if (this.lastSubmittedMelds().length > 0) {
          this.proposedMelds.set(this.lastSubmittedMelds());
          this.lastSubmittedMelds.set([]);
        }
      }
    });

    // Reset timer when current player changes
    effect(() => {
      const cur = this.view()?.current;
      if (cur === undefined) return;
      this.secondsLeft.set(120);
    });

    // Tick down once per second; fire FORCE_AUTO on 0 if my turn
    interval(1000).pipe(takeUntilDestroyed()).subscribe(() => {
      const v = this.view();
      if (!v || v.closed) return;
      const s = this.secondsLeft();
      if (s > 0) {
        this.secondsLeft.set(s - 1);
        if (s - 1 === 0 && this.isMyTurn()) {
          this.store.dispatch(Game.sendAction({
            gameId: this.gameId,
            action: { type: 'FORCE_AUTO', playerIdx: this.mySeatIdx() },
          }));
        }
      }
    });
  }

  ngOnDestroy(): void {
    this.store.dispatch(Game.clearGame());
  }

  togglePiece(id: number): void {
    const next = new Set(this.selectedIds());
    if (next.has(id)) next.delete(id); else next.add(id);
    this.selectedIds.set(next);
  }

  addMeld(): void {
    const hand = this.myPlayer()?.hand ?? [];
    const detected = detectMeld(hand, this.selectedIds());
    if (!detected) return;
    this.proposedMelds.update(prev => [...prev, detected]);
    this.selectedIds.set(new Set());
  }

  etalat(): void {
    const melds = this.proposedMelds();
    if (melds.length === 0) return;
    this.lastSubmittedMelds.set(melds);
    const action: Action = { type: 'ETALAT', playerIdx: this.mySeatIdx(), melds };
    this.store.dispatch(Game.sendAction({ gameId: this.gameId, action }));
    this.proposedMelds.set([]);
  }

  cancel(): void {
    this.selectedIds.set(new Set());
    this.proposedMelds.set([]);
  }

  draw(): void {
    this.store.dispatch(Game.sendAction({
      gameId: this.gameId,
      action: { type: 'DRAW_FROM_STOCK', playerIdx: this.mySeatIdx() },
    }));
  }

  discardPiece(pieceId: number): void {
    this.store.dispatch(Game.sendAction({
      gameId: this.gameId,
      action: { type: 'DISCARD', playerIdx: this.mySeatIdx(), pieceId },
    }));
  }

  takeDiscard(idx: number): void {
    this.store.dispatch(Game.sendAction({
      gameId: this.gameId,
      action: { type: 'TAKE_DISCARD', playerIdx: this.mySeatIdx(), discardIdx: idx },
    }));
  }

  layoff(args: { pieceId: number; meldIdx: number }): void {
    this.store.dispatch(Game.sendAction({
      gameId: this.gameId,
      action: { type: 'LAYOFF', playerIdx: this.mySeatIdx(), layoffs: [args] },
    }));
  }

  reorderHand(_args: { from: number; to: number }): void {
    // Hand reorder is purely visual; backend doesn't care.
    // For 4a/4b we don't persist order — could store locally in future.
  }

  roundEndResults(): RoundResult[] {
    const closedEvent = this.events().find((e: DomainEvent) => e.type === 'RoundClosed' || e['type'] === 'RoundClosed') as any;
    return closedEvent?.['results'] ?? [];
  }

  winnerName(): string {
    const r = this.roundEndResults();
    if (r.length === 0) return '';
    let best = 0;
    for (let i = 1; i < r.length; i++) if (r[i].base > r[best].base) best = i;
    return r[best].name;
  }

  closeRoundEnd(): void {
    window.location.href = '/lobby';
  }
}
```

- [ ] **Step 2: Write `game.page.html`**

```html
<ion-content class="ion-padding game-page">
  <app-error-banner [error]="error()"></app-error-banner>

  <ng-container *ngIf="view() as v">
    <div class="header-row">
      <app-opponents-bar
        [players]="v.players"
        [currentIdx]="v.current"
        [mySeatIdx]="mySeatIdx()"
        [totals]="v.totals">
      </app-opponents-bar>
      <app-turn-timer *ngIf="isMyTurn()" [secondsLeft]="secondsLeft()"></app-turn-timer>
    </div>

    <app-table-zone
      [atu]="v.atu"
      [stockCount]="v.stockCount"
      [discard]="v.discard"
      [melds]="v.melds"
      [canDraw]="canDraw()"
      [canTake]="canTake()"
      (drawClicked)="draw()"
      (takeRequested)="takeDiscard($event)"
      (pieceDroppedToDiscard)="discardPiece($event)"
      (layoffDropped)="layoff($event)">
    </app-table-zone>

    <ng-container *ngIf="myPlayer() as me">
      <app-hand
        [pieces]="me.hand"
        [selectedIds]="selectedIds()"
        [mustUsePieceId]="me.mustUsePieceId"
        [connectedTo]="handListConnectedTo()"
        (pieceClicked)="togglePiece($event)"
        (reorder)="reorderHand($event)">
      </app-hand>
    </ng-container>

    <app-action-bar
      [canDraw]="canDraw()"
      [selectedCount]="selectedIds().size"
      [canEtalat]="canEtalat()"
      [proposedMelds]="proposedMelds()"
      [mustUseHint]="mustUseHint()"
      (drawClicked)="draw()"
      (addMeldClicked)="addMeld()"
      (etalatClicked)="etalat()"
      (cancelClicked)="cancel()">
    </app-action-bar>
  </ng-container>

  <app-round-end-modal *ngIf="showRoundEnd() && roundEndResults().length > 0"
    [results]="roundEndResults()"
    [winnerName]="winnerName()"
    (closeRequested)="closeRoundEnd()">
  </app-round-end-modal>
</ion-content>
```

- [ ] **Step 3: Write `game.page.scss`**

```scss
.game-page {
  --background: #1a0f08;
  background: radial-gradient(ellipse at top, #3d2817 0%, #1a0f08 60%);
  color: #f4e8d0;
}
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
```

- [ ] **Step 4: Update routes**

In `frontend/src/app/app.routes.ts`, change:

```typescript
{ path: 'game/:id', canActivate: [authGuard],
  loadComponent: () => import('./features/game/game-debug.page') },
```

to:

```typescript
{ path: 'game/:id', canActivate: [authGuard],
  loadComponent: () => import('./features/game/game.page') },
```

- [ ] **Step 5: Delete old placeholder files**

```bash
cd /Users/georgesand/IdeaProjects/remi/frontend
rm src/app/features/game/game-debug.page.ts
rm src/app/features/game/game-debug.page.html
rm src/app/features/game/game-debug.page.scss
```

- [ ] **Step 6: Build + commit**

```bash
npx ng build --configuration=development
git add src/app/features/game/game.page.* \
        src/app/app.routes.ts
git rm src/app/features/game/game-debug.page.ts \
       src/app/features/game/game-debug.page.html \
       src/app/features/game/game-debug.page.scss 2>/dev/null || true
git commit -m "feat(game): replace GameDebugPage with real GamePage orchestrator

Wires all 4b components: opponents-bar, table-zone, hand, turn-timer,
action-bar, round-end-modal. Local UI state via signals (selectedIds,
proposedMelds, secondsLeft). Server state from NgRx Game store unchanged.

Includes hybrid 120s client timer with ForceAuto dispatch on zero
(backend server fallback at 180s remains safety net per Stage 3).
"
```

---

## Phase J — Smoke test + README

### Task J1: Extend SMOKE_TEST.md + frontend README

**Files:**
- Modify: `frontend/SMOKE_TEST.md`
- Modify: `frontend/README.md`

- [ ] **Step 1: Append to `SMOKE_TEST.md`**

After the existing "Matchmaking" section, add:

```markdown
## Stage 4b — Game UI scenarios

### Tap-to-select etalat

1. As Alice, after Bob joins and game starts, locate 3 consecutive RED pieces in your hand (e.g. 5/6/7).
2. Tap each — they lift up with a yellow glow.
3. Action bar shows "+ Adaugă meld" (green) — tap it.
4. Selection clears; "Etalează (1)" button (warning color) appears.
5. Repeat to add another meld if you have 45+ points worth. Otherwise tap "Etalează (1)".
6. Backend may reject if total <45p (first meld) → toast "Prima etalare are X<45p" + proposed meld restored. Adjust and retry.
7. On success: meld appears in table area; hand shrinks; current advances to next player.

### Etalat invalid

1. Tap 2 RED pieces that are NOT consecutive (e.g. 5 and 8).
2. "+ Adaugă meld" button does NOT appear (detectMeld returns null client-side).
3. No backend roundtrip needed.

### Layoff drag&drop

1. As Bob with hasEtalat=true, drag a piece from your hand onto an existing meld card (e.g. Alice's group of 7s).
2. Drop target turns green during drag (cdkDropList highlight).
3. Drop: backend processes; piece appears in meld; hand reduces.
4. If invalid (e.g. piece doesn't fit): toast "Piesa nu se potrivește."

### Take discard with break-line

1. As Alice with hasEtalat=true and hand≥4, the discard pile shows count + top piece.
2. Tap top piece (canTake=true → click handler fires).
3. Backend returns multiple pieces (the broken row): top + everything above the chosen index.
4. Hand grows; chosen piece has red border (mustUsePieceId).
5. You must use that piece in a meld/layoff before discarding else server rejects with MUST_USE_TAKEN_PIECE.

### Round-end modal

1. Play until you close the round (etalat your second-to-last piece + discard the last, or play your last via etalat that empties the hand).
2. View updates with `closed: true`.
3. Modal pops up with results table (each player + round score + total) + winner highlighted.
4. Tap "Înapoi la lobby" → /lobby.

### Hybrid timer

1. Start your turn, don't act for 120 seconds. Timer in header counts down.
2. At 0, client auto-dispatches FORCE_AUTO. Server processes (auto-draw or auto-discard depending on phase).
3. If client stalls (e.g. tab backgrounded), server fallback fires at 180s.
```

- [ ] **Step 2: Update `frontend/README.md`**

Replace the "## Architecture" section's mention of `GameDebugPage` with:

```markdown
## Architecture

```
src/app/
  core/        api/, ws/, auth/, models/, i18n/
  store/       NgRx (auth, lobby, match, game)
  features/
    auth/      Login, Register, VerifyEmail, RequestReset, ResetPassword
    lobby/     LobbyHome, CreateGame, JoinByCode, PublicList, QuickMatch
    game/      GamePage + 12 sub-components (Stage 4b real game UI)
  shared/      ErrorBanner, WsIndicator, GlobalErrorHandler
```

Stage 4b replaces the previous GameDebugPage placeholder. Game state is consumed
from the existing NgRx Game store; UI state (selected pieces, proposed melds,
seconds left) is local to the GamePage component via signals.
```

- [ ] **Step 3: Commit**

```bash
cd /Users/georgesand/IdeaProjects/remi
git add frontend/SMOKE_TEST.md frontend/README.md
git commit -m "docs(frontend): SMOKE_TEST scenarios for Stage 4b + README architecture update"
```

---

## Stage 4b complete

After all tasks:
- `GameDebugPage` replaced with real `GamePage`
- 12 standalone components (piece, hand, meld-card, stock-pile, discard-pile, atu-display, melds-area, table-zone, opponent-summary, opponents-bar, turn-timer, action-bar, round-end-modal)
- Pure helpers (pieces.utils, meld-detection) with extensive unit tests
- 4 Angular animation triggers
- Hybrid 120s client timer + ForceAuto auto-dispatch
- Tap-to-select etalat with batch submission + rollback on error
- CDK drag&drop for discard + layoff
- SMOKE_TEST.md extended with 5 new scenarios

**Next stage** (5 — mobile build via Capacitor): brainstorm when ready.
