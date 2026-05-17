import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { IonContent, IonInput, IonItem, IonList, IonLabel, IonButton, IonSelect,
         IonSelectOption, IonCard, IonCardHeader, IonCardTitle, IonCardContent,
         ToastController } from '@ionic/angular/standalone';
import { Actions, ofType } from '@ngrx/effects';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Game } from '../../store/game/game.actions';
import { selectGameView, selectGameError } from '../../store/game/game.selectors';
import { ErrorBannerComponent } from '../../shared/error-banner/error-banner.component';
import { ErrorMessagePipe } from '../../core/i18n/error-message.pipe';
import { Action, ActionType } from '../../core/models';

@Component({
  selector: 'app-game-debug',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonInput, IonItem, IonList, IonLabel, IonButton, IonSelect, IonSelectOption,
    IonCard, IonCardHeader, IonCardTitle, IonCardContent,
    ErrorBannerComponent, ErrorMessagePipe,
  ],
  templateUrl: './game-debug.page.html',
  styleUrls: ['./game-debug.page.scss'],
})
export default class GameDebugPage implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly toasts = inject(ToastController);
  private readonly actions$ = inject(Actions);

  readonly view$ = this.store.select(selectGameView);
  readonly error$ = this.store.select(selectGameError);

  readonly actionTypes: ActionType[] = ['DRAW_FROM_STOCK', 'TAKE_DISCARD', 'DISCARD', 'FORCE_AUTO'];

  readonly form = this.fb.nonNullable.group({
    type: ['DRAW_FROM_STOCK' as ActionType, [Validators.required]],
    playerIdx: [0, [Validators.required, Validators.min(0)]],
    pieceId: [0],         // only used for DISCARD
    discardIdx: [0],      // only used for TAKE_DISCARD
  });

  gameId = '';

  constructor() {
    this.actions$.pipe(ofType(Game.errorReceived), takeUntilDestroyed())
        .subscribe(({ error }) => {
          this.toasts.create({
            message: error.message,
            duration: 4000,
            color: 'danger',
          }).then(t => t.present());
        });
  }

  ngOnInit(): void {
    this.gameId = this.route.snapshot.paramMap.get('id') ?? '';
    if (this.gameId) {
      this.store.dispatch(Game.subscribeToGame({ gameId: this.gameId }));
      this.store.dispatch(Game.subscribeToErrors());
      this.store.dispatch(Game.loadGameRequested({ gameId: this.gameId }));
    }
  }

  ngOnDestroy(): void {
    this.store.dispatch(Game.clearGame());
  }

  submit(): void {
    if (this.form.invalid) return;
    const { type, playerIdx, pieceId, discardIdx } = this.form.getRawValue();
    let action: Action;
    switch (type) {
      case 'DRAW_FROM_STOCK': action = { type, playerIdx }; break;
      case 'TAKE_DISCARD': action = { type, playerIdx, discardIdx }; break;
      case 'DISCARD': action = { type, playerIdx, pieceId }; break;
      case 'FORCE_AUTO': action = { type, playerIdx }; break;
      default: return;
    }
    this.store.dispatch(Game.sendAction({ gameId: this.gameId, action }));
  }

  formatJson(v: unknown): string { return JSON.stringify(v, null, 2); }
}
