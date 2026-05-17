import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { IonContent, IonRadioGroup, IonRadio, IonItem, IonList, IonLabel,
         IonButton, IonRange, IonSelect, IonSelectOption } from '@ionic/angular/standalone';
import { Lobby } from '../../store/lobby/lobby.actions';
import { selectLobbyError, selectLobbyLoading } from '../../store/lobby/lobby.selectors';
import { ErrorBannerComponent } from '../../shared/error-banner/error-banner.component';
import { GameVisibility, Mode, Difficulty } from '../../core/models';

@Component({
  selector: 'app-create-game',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    IonContent, IonRadioGroup, IonRadio, IonItem, IonList, IonLabel,
    IonButton, IonRange, IonSelect, IonSelectOption,
    ErrorBannerComponent,
  ],
  templateUrl: './create-game.page.html',
})
export default class CreateGamePage {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);

  readonly form = this.fb.nonNullable.group({
    visibility: ['PRIVATE' as GameVisibility, [Validators.required]],
    numPlayers: [2, [Validators.required, Validators.min(2), Validators.max(6)]],
    mode: ['ETALAT' as Mode, [Validators.required]],
    difficulty: ['MED' as Difficulty, [Validators.required]],
  });

  readonly error$ = this.store.select(selectLobbyError);
  readonly loading$ = this.store.select(selectLobbyLoading);

  submit(): void {
    if (this.form.invalid) return;
    this.store.dispatch(Lobby.createRequested({ req: this.form.getRawValue() }));
  }
}
