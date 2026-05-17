import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { IonContent, IonInput, IonItem, IonList, IonLabel, IonButton }
    from '@ionic/angular/standalone';
import { Lobby } from '../../store/lobby/lobby.actions';
import { selectLobbyError, selectLobbyLoading } from '../../store/lobby/lobby.selectors';
import { ErrorBannerComponent } from '../../shared/error-banner/error-banner.component';

@Component({
  selector: 'app-join-by-code',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    IonContent, IonInput, IonItem, IonList, IonLabel, IonButton,
    ErrorBannerComponent,
  ],
  templateUrl: './join-by-code.page.html',
})
export default class JoinByCodePage {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  readonly form = this.fb.nonNullable.group({
    joinCode: ['', [Validators.required, Validators.pattern(/^[A-Z0-9]{8}$/)]],
  });
  readonly error$ = this.store.select(selectLobbyError);
  readonly loading$ = this.store.select(selectLobbyLoading);

  submit(): void {
    if (this.form.invalid) return;
    this.store.dispatch(Lobby.joinByCodeRequested({ joinCode: this.form.getRawValue().joinCode }));
  }
}
