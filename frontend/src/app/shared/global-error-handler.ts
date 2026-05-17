import { ErrorHandler, Injectable, inject } from '@angular/core';
import { ToastController } from '@ionic/angular/standalone';

@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  private readonly toasts = inject(ToastController);

  handleError(error: unknown): void {
    console.error('[GlobalErrorHandler]', error);
    this.toasts.create({
      message: 'A apărut o eroare neașteptată.',
      duration: 4000,
      color: 'danger',
    }).then(t => t.present()).catch(() => { /* swallow toast errors */ });
  }
}
