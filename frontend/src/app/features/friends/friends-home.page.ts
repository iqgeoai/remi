import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonList, IonItem, IonLabel,
  IonButton, IonBadge,
} from '@ionic/angular/standalone';
import { FriendsActions } from '../../store/friends/friends.actions';
import { friendsFeature } from '../../store/friends/friends.reducer';
import { FriendsApi } from '../../core/api/friends.api';

/**
 * Hub for the friends feature. Renders three nav items (search, requests,
 * blocked) and the current friends list with online/offline status. Each
 * friend row exposes an Invită button — see {@link FriendsHomePage.invite}.
 */
@Component({
  selector: 'app-friends-home',
  standalone: true,
  imports: [
    CommonModule, RouterLink,
    IonContent, IonHeader, IonToolbar, IonTitle, IonList, IonItem, IonLabel,
    IonButton, IonBadge,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ion-header><ion-toolbar><ion-title>Prieteni</ion-title></ion-toolbar></ion-header>
    <ion-content class="ion-padding">
      <ion-list>
        <ion-item button routerLink="/friends/search">
          <ion-label>Caută prieteni</ion-label>
        </ion-item>
        <ion-item button routerLink="/friends/requests">
          <ion-label>Cereri</ion-label>
          <ion-badge *ngIf="(incoming$ | async)?.length as n" slot="end">{{ n }}</ion-badge>
        </ion-item>
        <ion-item button routerLink="/friends/blocked">
          <ion-label>Blocați</ion-label>
        </ion-item>
        <ion-item button routerLink="/friends/dm">
          <ion-label>Mesaje</ion-label>
        </ion-item>
      </ion-list>

      <ion-list>
        <ion-item button *ngFor="let f of friends$ | async" [routerLink]="['/profile', f.id]">
          <ion-label>
            <h2>{{ f.username }}</h2>
            <p>{{ f.online ? 'online' : 'offline' }}</p>
          </ion-label>
          <ion-button slot="end" (click)="invite(f.id); $event.stopPropagation()">Invită</ion-button>
        </ion-item>
      </ion-list>
    </ion-content>
  `,
})
export default class FriendsHomePage implements OnInit {
  private readonly store = inject(Store);
  private readonly api = inject(FriendsApi);
  private readonly router = inject(Router);
  readonly friends$ = this.store.select(friendsFeature.selectFriends);
  readonly incoming$ = this.store.select(friendsFeature.selectIncoming);

  ngOnInit(): void {
    this.store.dispatch(FriendsActions.loadFriends());
    this.store.dispatch(FriendsActions.loadRequests());
  }

  /**
   * POST /api/friends/{id}/invite with default settings. The backend creates
   * a private match owned by us (we are auto-seated) and pushes a WS invite
   * to the friend. We navigate straight to the game route to wait for the
   * friend to join via the WS deep link (handled by FriendsWsBridge).
   */
  async invite(friendId: string): Promise<void> {
    const { matchId } = await this.api.invite(friendId);
    await this.router.navigateByUrl(`/game/${matchId}`);
  }
}
