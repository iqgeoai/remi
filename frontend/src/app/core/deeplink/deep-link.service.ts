import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { App, URLOpenListenerEvent } from '@capacitor/app';

@Injectable({ providedIn: 'root' })
export class DeepLinkService {
  private readonly router = inject(Router);

  async init(): Promise<void> {
    await App.addListener('appUrlOpen', (event: URLOpenListenerEvent) => {
      this.handleUrl(event.url);
    });
  }

  handleUrl(url: string): void {
    if (!url.startsWith('remi://')) return;
    const path = url.substring('remi://'.length);
    const [host, ...rest] = path.split('/');
    if (host === 'match' && rest[0]) {
      this.router.navigateByUrl(`/game/${rest[0]}`);
    } else if (host === 'invite' && rest[0]) {
      this.router.navigateByUrl(`/lobby/join-by-code?code=${rest[0]}`);
    }
  }
}
