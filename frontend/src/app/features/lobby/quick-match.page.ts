import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { IonContent, IonRadioGroup, IonRadio, IonItem, IonList, IonLabel,
         IonButton, IonRange, IonSelect, IonSelectOption } from '@ionic/angular/standalone';
import { Match } from '../../store/match/match.actions';
import { selectMatchError, selectMatchStatus } from '../../store/match/match.selectors';
import { ErrorBannerComponent } from '../../shared/error-banner/error-banner.component';
import { Mode, Difficulty } from '../../core/models';

@Component({
  selector: 'app-quick-match',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    IonContent, IonRadioGroup, IonRadio, IonItem, IonList, IonLabel,
    IonButton, IonRange, IonSelect, IonSelectOption,
    ErrorBannerComponent,
  ],
  templateUrl: './quick-match.page.html',
})
export default class QuickMatchPage implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);

  readonly form = this.fb.nonNullable.group({
    numPlayers: [2, [Validators.required, Validators.min(2), Validators.max(6)]],
    mode: ['ETALAT' as Mode, [Validators.required]],
    difficulty: ['MED' as Difficulty, [Validators.required]],
  });
  readonly status$ = this.store.select(selectMatchStatus);
  readonly error$ = this.store.select(selectMatchError);

  ngOnInit(): void {
    // CRITICAL: subscribe to match topic BEFORE user clicks Find — server may
    // push a match notification before the HTTP /quick response if another
    // user is already queued.
    this.store.dispatch(Match.subscribeToMatchTopic());
  }

  find(): void {
    if (this.form.invalid) return;
    this.store.dispatch(Match.quickRequested({ req: this.form.getRawValue() }));
  }

  cancel(): void {
    this.store.dispatch(Match.cancelRequested());
  }
}
