import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Store } from '@ngrx/store';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonList, IonItem, IonLabel,
  IonButton, IonSegment, IonSegmentButton,
} from '@ionic/angular/standalone';
import { FriendsActions } from '../../store/friends/friends.actions';
import { friendsFeature } from '../../store/friends/friends.reducer';

/**
 * Two-tab view over pending friend requests:
 *   - Primite — incoming requests addressed to the current user (accept/refuse)
 *   - Trimise — outgoing requests sent by the current user (cancel)
 *
 * Each action dispatches the matching {@link FriendsActions}; effects then
 * re-fetch the request lists so the segment stays in sync.
 */
@Component({
  selector: 'app-friend-requests',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    IonContent, IonHeader, IonToolbar, IonTitle, IonList, IonItem, IonLabel,
    IonButton, IonSegment, IonSegmentButton,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ion-header><ion-toolbar><ion-title>Cereri</ion-title></ion-toolbar></ion-header>
    <ion-content class="ion-padding">
      <ion-segment [(ngModel)]="tab">
        <ion-segment-button value="in"><ion-label>Primite</ion-label></ion-segment-button>
        <ion-segment-button value="out"><ion-label>Trimise</ion-label></ion-segment-button>
      </ion-segment>

      <ion-list *ngIf="tab === 'in'">
        <ion-item *ngFor="let r of incoming$ | async">
          <ion-label>{{ r.username }}</ion-label>
          <ion-button slot="end" (click)="accept(r.id)">Acceptă</ion-button>
          <ion-button slot="end" color="medium" (click)="reject(r.id)">Refuză</ion-button>
        </ion-item>
      </ion-list>

      <ion-list *ngIf="tab === 'out'">
        <ion-item *ngFor="let r of outgoing$ | async">
          <ion-label>{{ r.username }}</ion-label>
          <ion-button slot="end" color="medium" (click)="cancel(r.id)">Anulează</ion-button>
        </ion-item>
      </ion-list>
    </ion-content>
  `,
})
export default class FriendRequestsPage implements OnInit {
  private readonly store = inject(Store);
  readonly incoming$ = this.store.select(friendsFeature.selectIncoming);
  readonly outgoing$ = this.store.select(friendsFeature.selectOutgoing);
  tab: 'in' | 'out' = 'in';

  ngOnInit(): void {
    this.store.dispatch(FriendsActions.loadRequests());
  }

  accept(id: number): void { this.store.dispatch(FriendsActions.acceptRequest({ id })); }
  reject(id: number): void { this.store.dispatch(FriendsActions.rejectRequest({ id })); }
  cancel(id: number): void { this.store.dispatch(FriendsActions.cancelRequest({ id })); }
}
