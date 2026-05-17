import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { IonContent, IonInput, IonButton, IonItem, IonList, IonLabel }
    from '@ionic/angular/standalone';
import { Auth } from '../../store/auth/auth.actions';
import { selectAuthError, selectAuthStatus } from '../../store/auth/auth.selectors';
import { ErrorBannerComponent } from '../../shared/error-banner/error-banner.component';
import { Actions, ofType } from '@ngrx/effects';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-register-page',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonInput, IonButton, IonItem, IonList, IonLabel,
    ErrorBannerComponent,
  ],
  templateUrl: './register.page.html',
  styleUrls: ['../auth/login.page.scss'],
})
export default class RegisterPage {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly actions$ = inject(Actions);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(20),
                    Validators.pattern(/^[a-zA-Z0-9_-]+$/)]],
    password: ['', [Validators.required, Validators.minLength(10)]],
  });

  readonly error$ = this.store.select(selectAuthError);
  readonly status$ = this.store.select(selectAuthStatus);

  registered = false;

  constructor() {
    this.actions$.pipe(
      ofType(Auth.registerSucceeded),
      takeUntilDestroyed(),
    ).subscribe(() => { this.registered = true; });
  }

  submit(): void {
    if (this.form.invalid) return;
    const { email, username, password } = this.form.getRawValue();
    this.store.dispatch(Auth.registerRequested({ email, username, password }));
  }
}
