import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { IonApp, IonRouterOutlet, IonHeader, IonToolbar, IonTitle, IonButton, IonButtons, IonContent }
    from '@ionic/angular/standalone';
import { Auth } from './store/auth/auth.actions';
import { selectIsAuthenticated, selectUser } from './store/auth/auth.selectors';
import { StompService } from './core/ws/stomp.service';
import { AuthStorageService } from './core/auth/auth-storage.service';
import { DeepLinkService } from './core/deeplink/deep-link.service';
import { WsIndicatorComponent } from './shared/ws-indicator/ws-indicator.component';
import { Observable } from 'rxjs';
import { User } from './core/models';
import { WsConnectionState } from './core/ws/ws-state';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule, RouterOutlet,
    IonApp, IonRouterOutlet, IonHeader, IonToolbar, IonTitle, IonButton, IonButtons, IonContent,
    WsIndicatorComponent,
  ],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss'],
})
export class AppComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly router = inject(Router);
  private readonly stomp = inject(StompService);
  private readonly authStorage = inject(AuthStorageService);
  private readonly deepLink = inject(DeepLinkService);

  readonly isAuthenticated$: Observable<boolean> = this.store.select(selectIsAuthenticated);
  readonly user$: Observable<User | null> = this.store.select(selectUser);
  readonly wsState$: Observable<WsConnectionState> = this.stomp.connectionState$;

  async ngOnInit(): Promise<void> {
    // Reconcile tokens between native secure storage and localStorage
    // (no-op on web when both empty) before kicking off bootstrap.
    await this.authStorage.migrateLegacyToken();
    // Register the `remi://` URL handler. Fire-and-forget: failure on web
    // (no native App plugin) is acceptable and surfaces only as a console log.
    void this.deepLink.init();
    this.store.dispatch(Auth.bootstrapFromStorage());
  }

  logout(): void {
    this.store.dispatch(Auth.logoutRequested());
  }
}
