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
