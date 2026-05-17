import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { IonApp, IonRouterOutlet, IonHeader, IonToolbar, IonTitle, IonButton, IonButtons, IonContent }
    from '@ionic/angular/standalone';
import { Auth } from './store/auth/auth.actions';
import { selectIsAuthenticated, selectUser } from './store/auth/auth.selectors';
import { StompService } from './core/ws/stomp.service';
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

  readonly isAuthenticated$: Observable<boolean> = this.store.select(selectIsAuthenticated);
  readonly user$: Observable<User | null> = this.store.select(selectUser);
  readonly wsState$: Observable<WsConnectionState> = this.stomp.connectionState$;

  ngOnInit(): void {
    this.store.dispatch(Auth.bootstrapFromStorage());
  }

  logout(): void {
    this.store.dispatch(Auth.logoutRequested());
  }
}
