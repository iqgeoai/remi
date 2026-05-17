import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { IonContent, IonInput, IonButton, IonItem, IonList, IonLabel, IonNote }
    from '@ionic/angular/standalone';
import { Auth } from '../../store/auth/auth.actions';
import { selectAuthError, selectAuthStatus, selectLastInvalidationReason } from '../../store/auth/auth.selectors';
import { ErrorBannerComponent } from '../../shared/error-banner/error-banner.component';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, RouterLink,
    IonContent, IonInput, IonButton, IonItem, IonList, IonLabel, IonNote,
    ErrorBannerComponent,
  ],
  templateUrl: './login.page.html',
  styleUrls: ['./login.page.scss'],
})
export default class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);

  readonly form = this.fb.nonNullable.group({
    emailOrUsername: ['', [Validators.required]],
    password: ['', [Validators.required]],
  });

  readonly error$ = this.store.select(selectAuthError);
  readonly status$ = this.store.select(selectAuthStatus);
  readonly invalidationReason$ = this.store.select(selectLastInvalidationReason);

  submit(): void {
    if (this.form.invalid) return;
    const { emailOrUsername, password } = this.form.getRawValue();
    this.store.dispatch(Auth.loginRequested({ emailOrUsername, password }));
  }
}
