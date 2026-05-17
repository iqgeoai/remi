import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { IonContent, IonInput, IonButton, IonItem, IonList, IonLabel }
    from '@ionic/angular/standalone';
import { Auth } from '../../store/auth/auth.actions';
import { selectAuthError, selectAuthStatus } from '../../store/auth/auth.selectors';
import { ErrorBannerComponent } from '../../shared/error-banner/error-banner.component';
import { Actions, ofType } from '@ngrx/effects';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-reset-password-page',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonInput, IonButton, IonItem, IonList, IonLabel,
    ErrorBannerComponent,
  ],
  templateUrl: './reset-password.page.html',
  styleUrls: ['../auth/login.page.scss'],
})
export default class ResetPasswordPage implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly actions$ = inject(Actions);

  readonly form = this.fb.nonNullable.group({
    token: ['', [Validators.required]],
    newPassword: ['', [Validators.required, Validators.minLength(10)]],
  });
  readonly error$ = this.store.select(selectAuthError);
  readonly status$ = this.store.select(selectAuthStatus);
  done = false;

  constructor() {
    this.actions$.pipe(ofType(Auth.resetPasswordSucceeded), takeUntilDestroyed())
        .subscribe(() => { this.done = true; });
  }

  ngOnInit(): void {
    const queryToken = this.route.snapshot.queryParamMap.get('token');
    if (queryToken) this.form.patchValue({ token: queryToken });
  }

  submit(): void {
    if (this.form.invalid) return;
    const { token, newPassword } = this.form.getRawValue();
    this.store.dispatch(Auth.resetPasswordRequested({ token, newPassword }));
  }
}
