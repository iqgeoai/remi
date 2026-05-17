# Stage 4b — Game UI Port (Design)

**Data:** 2026-05-17
**Status:** Approved
**Scope:** Stage 4b. Înlocuiește `GameDebugPage` (placeholder din Stage 4a) cu UI real pentru jocul Remi — port din `assets/remi.html` în Angular components. Mobile build (iOS+Android via Capacitor) rămâne Stage 5.

## Context

Stage 1-3 au livrat backend complet. Stage 4a a livrat frontend shell (Ionic+Angular+NgRx) cu auth, lobby, WS, și o pagină placeholder de joc care arată GameView ca JSON și permite trimiterea de Action prin formular.

Stage 4b înlocuiește placeholder-ul cu UI real: opponents bar, table cu melds+atu+stock+discard, hand sortable, drag&drop pentru toate acțiunile, timer vizual, modal end-round. State management (NgRx Game store) și WebSocket integration sunt deja făcute în 4a — 4b consume aceste contracte stabile.

## Cerințe (confirmate cu user)

- **Scope**: **MVP + animații + end-modal** (NU sound)
- **Drag & drop**: **Angular CDK Drag&Drop** (`@angular/cdk/drag-drop`)
- **Layout**: **mobile-first portrait** (compatibil cu Stage 5 mobile build)
- **Etalat UX**: **tap-to-select** multi-piece + buton "+ Adaugă meld" + "Etalează (X)" pentru batch
- **Animații**: **plus motion** — draw card slides from stock, discard slides; plus piece-selected glow, turn-change highlight

## Stack adițional în 4b

- `@angular/cdk@^20` (cdk/drag-drop)
- `@angular/animations` (deja inclus în Angular core)

NU sound (Web Audio API deferat).

## 1. Arhitectură

Înlocuiește `frontend/src/app/features/game/game-debug.page.*` (Stage 4a placeholder) cu o pagină reală + ~12 sub-componente.

```
frontend/src/app/features/game/
  game.page.ts              ← parent orchestrator
  components/
    opponents-bar/
      opponents-bar.component.ts
      opponent-summary.component.ts
    table-zone/
      table-zone.component.ts
      atu-display.component.ts
      stock-pile.component.ts
      discard-pile.component.ts
      melds-area.component.ts
      meld-card.component.ts
    hand/
      hand.component.ts
      piece.component.ts
    turn-timer/
      turn-timer.component.ts
    action-bar/
      action-bar.component.ts
    round-end-modal/
      round-end-modal.component.ts
shared/
  pieces.utils.ts             ← getPieceColor(), formatPiece()
  meld-detection.ts           ← detectMeld(hand, selectedIds) → MeldProposal | null
animations/
  game.animations.ts          ← Angular animation triggers
```

**State management:**
- **NgRx Game store** (existent din 4a) păstrează `GameView`, `events`, `error` din server
- **UI state local** (Signals în GamePage) pentru:
  - `selectedIds: signal<Set<number>>` — selecție multi
  - `proposedMelds: signal<MeldProposal[]>` — meld-uri puse în queue
  - `lastSubmittedMelds: signal<MeldProposal[]>` — pentru rollback pe error
  - `secondsLeft: signal<number>` — timer countdown

**Regulă păstrată:** componentele NU apelează direct API/WS. Tot prin `store.dispatch()` + `select()`. `Game.sendAction` rămâne calea pentru a trimite mutări (existent 4a).

**Routing:** `app.routes.ts` neschimbat — `{ path: 'game/:id', loadComponent: ... }`; doar conținutul fișierului se schimbă.

## 2. Componente și API-uri publice

### 2.1 `<app-piece>` (atomic vizual)

```typescript
@Component({ selector: 'app-piece', standalone: true, /*...*/, changeDetection: ChangeDetectionStrategy.OnPush })
export class PieceComponent {
  readonly piece = input.required<Piece>();
  readonly selected = input<boolean>(false);
  readonly select = output<number>();
}
```

Template: `<div class="piece" [class.selected]="selected()" [class.joker]="piece().isJoker" [attr.data-color]="piece().color"><span class="num">{{ piece().num }}</span></div>` (joker afișează `★`).

Style: 4 culori (red/yellow/blue/black + joker rainbow). Selected = border galben pulsing + lift 4px.

### 2.2 `<app-hand>`

```typescript
export class HandComponent {
  readonly pieces = input.required<Piece[]>();
  readonly selectedIds = input<Set<number>>(new Set());
  readonly mustUsePieceId = input<number | null>(null);
  readonly pieceClicked = output<number>();
  readonly pieceDropToDiscard = output<number>();
  readonly pieceDropToMeld = output<{pieceId: number; meldIdx: number}>();
  readonly reorder = output<{from: number; to: number}>();
}
```

Internal: `cdkDropList` cu fiecare `<app-piece cdkDrag>`.

### 2.3 `<app-meld-card>`

```typescript
export class MeldCardComponent {
  readonly meld = input.required<Meld>();
  readonly meldIdx = input.required<number>();
  readonly layoffDropped = output<{pieceId: number; meldIdx: number}>();
}
```

`cdkDropList` care primește pieces din hand (layoff).

### 2.4 Table sub-components

```typescript
export class StockPileComponent {
  readonly count = input.required<number>();
  readonly canDraw = input<boolean>(false);
  readonly clicked = output<void>();
}

export class DiscardPileComponent {
  readonly discard = input.required<Piece[]>();
  readonly canTake = input<boolean>(false);
  readonly takeRequested = output<number>();
}

export class AtuDisplayComponent {
  readonly atu = input.required<Piece>();
}
```

### 2.5 Opponents

```typescript
export class OpponentsBarComponent {
  readonly players = input.required<PlayerView[]>();
  readonly currentIdx = input.required<number>();
  readonly mySeatIdx = input.required<number>();
}

export class OpponentSummaryComponent {
  readonly player = input.required<PlayerView>();
  readonly active = input<boolean>(false);
  readonly score = input<number>(0);
}
```

Afișează: nume, hand count, score, green dot dacă hasEtalat, glow gold dacă active.

### 2.6 Turn timer

```typescript
export class TurnTimerComponent {
  readonly secondsLeft = input.required<number>();
  readonly totalSeconds = input<number>(120);
}
```

SVG circle cu stroke-dashoffset animation.

### 2.7 Action bar

```typescript
export class ActionBarComponent {
  readonly canDraw = input<boolean>(false);
  readonly selectedCount = input<number>(0);
  readonly canEtalat = input<boolean>(false);
  readonly proposedMelds = input<MeldProposal[]>([]);
  readonly drawClicked = output<void>();
  readonly addMeldClicked = output<void>();
  readonly etalatClicked = output<void>();
  readonly cancelClicked = output<void>();
}
```

Butoane vizibile dinamic:
- Phase DRAW: "Trage de la pachet"
- Phase ACTION/DISCARD cu selection: "+ Adaugă meld" + "Etalează (X)" + "Anulează"
- Phase ACTION/DISCARD fără selection + fără mustUse: hint "Drag piesă pe pile"

### 2.8 Round end modal

```typescript
export class RoundEndModalComponent {
  readonly results = input.required<RoundResult[]>();
  readonly winnerName = input.required<string>();
  readonly closeRequested = output<void>();
}
```

Tabel cu fiecare jucător + punctele rundei + totalul cumulat. Buton "Înapoi la lobby".

### 2.9 GamePage (orchestrator)

```typescript
export default class GamePage implements OnInit, OnDestroy {
  readonly view = this.store.selectSignal(selectGameView);
  readonly events = this.store.selectSignal(selectGameEvents);
  readonly error = this.store.selectSignal(selectGameError);

  readonly mySeatIdx = computed(/* find seat by user.username */);
  readonly isMyTurn = computed(() => this.view()?.current === this.mySeatIdx());
  readonly myPlayer = computed(() => this.view()?.players[this.mySeatIdx()] ?? null);

  readonly selectedIds = signal<Set<number>>(new Set());
  readonly proposedMelds = signal<MeldProposal[]>([]);
  readonly lastSubmittedMelds = signal<MeldProposal[]>([]);
  readonly secondsLeft = signal(120);

  readonly canDraw = computed(() => this.isMyTurn() && this.view()?.phase === 'DRAW');
  readonly canEtalat = computed(() =>
    detectMeld(this.myPlayer()?.hand ?? [], this.selectedIds()) !== null);
}
```

Handlers: `togglePiece`, `addMeld`, `etalat`, `draw`, `discardPiece`, `layoff`, `takeDiscard`.

### 2.10 Helper pur `meld-detection.ts`

```typescript
export function detectMeld(hand: Piece[], selectedIds: Set<number>): MeldProposal | null;
```

TypeScript port al logicii din backend `MeldValidator` (~50 linii). Testat în izolare.

### 2.11 Animations `game.animations.ts`

- `pieceSelectedAnim`: lift + glow on selected state change
- `drawSlideAnim`: new piece flies from stock position (transform translate + rotate + fade-in 300ms)
- `discardSlideAnim`: piece slides into discard pile (translateY + fade-in 250ms)
- `opponentActiveAnim`: gradient sweep on opponent card when becomes active

## 3. Data flow

### 3.1 Loading game on enter

`GamePage.ngOnInit` dispatches existing 4a actions:
- `Game.subscribeToGame({gameId})` → WS topic
- `Game.subscribeToErrors()` → WS errors queue
- `Game.loadGameRequested({gameId})` → REST GET initial state

Plus 4b additions:
- effect: toast on `Game.errorReceived`
- effect: start interval ticker pentru turn timer

### 3.2 Tap-to-select

User taps piece → emits `select` → `HandComponent.pieceClicked` → `GamePage.togglePiece(id)`:
```typescript
const next = new Set(this.selectedIds());
if (next.has(id)) next.delete(id); else next.add(id);
this.selectedIds.set(next);
```

`canEtalat` recomputes; ActionBar updates.

### 3.3 "+ Adaugă meld"

`GamePage.addMeld()`:
1. `const detected = detectMeld(myPlayer().hand, this.selectedIds())`
2. dacă null → no-op (button era oricum disabled)
3. `proposedMelds.update(prev => [...prev, detected])`
4. `selectedIds.set(new Set())`

### 3.4 "Etalează"

`GamePage.etalat()`:
1. Build `Action.Etalat` cu `proposedMelds()`
2. `lastSubmittedMelds.set(proposedMelds())` (pentru rollback)
3. `store.dispatch(Game.sendAction({gameId, action}))`
4. `proposedMelds.set([])` (optimistic clear)
5. Effect pe `Game.errorReceived` (within ~500ms): rollback `proposedMelds.set(lastSubmittedMelds())`

### 3.5 Drag hand → discard

`HandComponent` CDK Drag&Drop event → `pieceDropToDiscard.emit(pieceId)` → `GamePage.discardPiece(pieceId)`:
```typescript
const action: Action = { type: 'DISCARD', playerIdx: mySeatIdx(), pieceId };
store.dispatch(Game.sendAction({gameId, action}));
```

### 3.6 Drag hand → meld (layoff)

`MeldCardComponent.layoffDropped({pieceId, meldIdx})` → `GamePage.layoff(pieceId, meldIdx)`:
```typescript
const action: Action = {
  type: 'LAYOFF', playerIdx: mySeatIdx(),
  layoffs: [{pieceId, meldIdx}],
};
store.dispatch(Game.sendAction({gameId, action}));
```

### 3.7 Tap stock

`StockPileComponent.clicked` → `GamePage.draw()`:
```typescript
store.dispatch(Game.sendAction({gameId, action: {type:'DRAW_FROM_STOCK', playerIdx: mySeatIdx()}}));
```

Anim: new piece în hand are `[@drawSlide]` → slides in.

### 3.8 Tap discard top

`DiscardPileComponent.takeRequested(idx)` → `GamePage.takeDiscard(idx)`:
```typescript
store.dispatch(Game.sendAction({gameId, action: {type:'TAKE_DISCARD', playerIdx: mySeatIdx(), discardIdx: idx}}));
```

### 3.9 Turn timer

```typescript
effect(() => {
  const v = view();
  if (!v) return;
  this.secondsLeft.set(120);
  interval(1000).pipe(takeWhile(() => this.secondsLeft() > 0))
    .subscribe(() => this.secondsLeft.update(s => s - 1));
});

effect(() => {
  if (this.secondsLeft() === 0 && this.isMyTurn()) {
    store.dispatch(Game.sendAction({
      gameId, action: {type: 'FORCE_AUTO', playerIdx: mySeatIdx()}
    }));
  }
});
```

Server fallback la 180s rămâne safety net.

### 3.10 Round end

`GamePage` effect: `if (view()?.closed) showRoundEndModal();`

`showRoundEndModal()` deschide `<app-round-end-modal>` cu `RoundClosed` event extras din `events()`. On close → navigate `/lobby`.

### 3.11 Error rollback

Toast pentru toate `Game.errorReceived`. Rollback `proposedMelds` la `lastSubmittedMelds` dacă era ETALAT în zbor.

## 4. Error handling

Reuses Stage 4a infrastructure (ERROR_MESSAGES, ErrorMessagePipe, ErrorBanner, GlobalErrorHandler, ToastController). 4b NU adaugă mecanisme noi.

### Stratificare pe surse

1. **Server WS errors**: `Game.errorReceived` → toast + rollback `proposedMelds`
2. **REST errors initial GET**: `Game.loadGameFailed` → `<app-error-banner>` cu buton "Reîncearcă". Dacă `NOT_SEATED` → navigate `/lobby` cu toast
3. **WS deconectată**: `WsIndicator` existent. Reconnect transparent prin StompService. Overlay "Reconectare..." dacă >10s
4. **Tap pe pile invalid**: client-side `canDraw`/`canTake` previne click; pile-uri vizual disabled
5. **Drag&drop invalid**: CDK `cdkDropListEnterPredicate` blochează drop; piesa face snap-back
6. **Game closed mid-action**: effect detectează `closed === true` → modal apare, interacțiunile dezactivate

### Logging policy

Identic 4a: `console.error` doar pentru excepții; niciun log de pieces specifice.

## 5. Testing

### Piramidă

```
Smoke manual (extins 4a)                ~6 scenarii noi
Component tests (TestBed)               ~15 teste
Unit (pure helpers)                     ~10 teste
```

### Unit tests (pure)

- **`meld-detection.spec.ts`** (~10 cases): GROUP valid/invalid, SUITE valid/invalid, wrap 12-13-1, joker mid/start, size<3, duplicate color
- **`pieces.utils.spec.ts`** (~3 cases): getPieceColor, formatPiece accessibility

### Component tests (TestBed)

- `PieceComponent`: render num/joker, selected class, click emit
- `HandComponent`: render N pieces, tap → emit, mustUse highlight, CDK reorder
- `MeldCardComponent`: render group/suite, drop event → emit
- `StockPileComponent`: render count, canDraw gates click
- `DiscardPileComponent`: render top piece, empty state, canTake gates
- `AtuDisplayComponent`: render piece, doubleGame badge când joker/1
- `OpponentSummaryComponent`: render name/score, active class, etalat dot
- `OpponentsBarComponent`: filter mySeat, N-1 renders
- `TurnTimerComponent`: SVG stroke offset proportional, critical class <=10s
- `ActionBarComponent`: buttons visible per state, emit per click
- `RoundEndModalComponent`: results table, winner highlight, close emit

### Integration test pentru GamePage

`GamePage.spec.ts`: setup cu `provideMockStore` + TEST_GAME_VIEW; verifică:
- HandComponent primește myPlayer.hand
- OpponentsBarComponent primește players exclusiv me
- StockPileComponent canDraw=true (DRAW phase + my turn)
- Tap piesă → selectedIds().has(id)
- Click "+ Adaugă meld" valid → proposedMelds().length === 1
- Click "Etalează" → store.dispatch cu Action.Etalat
- `Game.errorReceived` → toast.create called

### Smoke test extins (`frontend/SMOKE_TEST.md`)

6 scenarii noi adăugate după pașii 4a:

1. **Tap-to-select etalat**: tap 3 piese RED consecutive → highlight + buton activ → Etalează → meld pe table + hand reduce
2. **Etalat invalid**: 2 piese non-consecutive → buton rămâne disabled
3. **Etalat <45p**: meld valid dar <45p ca first → submit → toast + rollback
4. **Layoff drag&drop**: drag hand piesă pe meld → backend acceptă → piesa în meld
5. **Take discard cu break-line**: hasEtalat+hand≥4 trage din mijloc → multiple piese în hand cu mustUse → trebuie folosit înainte de discard
6. **Round-end modal**: închide cu ultima piesă → RoundClosed → modal cu scores → "Înapoi la lobby"

### Coverage gate

- Reuse policy din 4a: NU enforced în CI
- Target informativ: 60% pe `features/game/**`
- Reducer NgRx Game deja 90% din 4a; neschimbat

### Test infrastructure

`frontend/src/test-utils/game-test-data.ts`:
```typescript
export const TEST_PIECES = { red5: {...}, red6: {...}, /*10-15 helpers*/ };
export const TEST_GAME_VIEW: GameView = { /* 2 players, current=0, phase=DISCARD */ };
```

Reused în toate component tests.

## Excluderi explicite (deferate)

- **Sound effects** (Web Audio API port din remi.html) — backlog polish
- **Hand grouping pre-etalat** (drag pieces over each other să grupezi) — backlog UX
- **Hint system** (toast cu sugestii contextuale "Drag aici", "Selectează 3+...") — backlog
- **Spectator mode UI** — deferat Stage 8+
- **Chat in-game** — Stage 7
- **Mobile build** (iOS+Android Capacitor) — Stage 5
- **Migrare Karma → Jest** — backlog dacă suite devine lent
- **E2E în browser real** — Stage 5+
