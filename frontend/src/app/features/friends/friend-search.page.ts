import { ChangeDetectionStrategy, Component, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Store } from '@ngrx/store';
import { Subject, Subscription, debounceTime, distinctUntilChanged } from 'rxjs';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonSearchbar, IonList, IonItem,
  IonLabel, IonButton,
} from '@ionic/angular/standalone';
import { FriendsActions } from '../../store/friends/friends.actions';
import { friendsFeature } from '../../store/friends/friends.reducer';

/**
 * Username search with 250ms debounce. Empty/short queries (<2 chars) clear
 * results client-side instead of round-tripping the API. Send Cerere dispatches
 * {@link FriendsActions.sendRequest} and resets the search box.
 */
@Component({
  selector: 'app-friend-search',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    IonContent, IonHeader, IonToolbar, IonTitle, IonSearchbar, IonList, IonItem,
    IonLabel, IonButton,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ion-header><ion-toolbar><ion-title>Caută prieteni</ion-title></ion-toolbar></ion-header>
    <ion-content class="ion-padding">
      <ion-searchbar
        [(ngModel)]="q"
        (ionInput)="onInput()"
        placeholder="username..."
        debounce="0">
      </ion-searchbar>
      <ion-list>
        <ion-item *ngFor="let hit of hits$ | async">
          <ion-label>{{ hit.username }}</ion-label>
          <ion-button slot="end" (click)="send(hit.id)">Adaugă</ion-button>
        </ion-item>
      </ion-list>
    </ion-content>
  `,
})
export default class FriendSearchPage implements OnDestroy {
  private readonly store = inject(Store);
  readonly hits$ = this.store.select(friendsFeature.selectSearchHits);
  q = '';
  private readonly input$ = new Subject<string>();
  private readonly sub: Subscription;

  constructor() {
    this.sub = this.input$.pipe(
      debounceTime(250),
      distinctUntilChanged(),
    ).subscribe(q => {
      if (q && q.length >= 2) {
        this.store.dispatch(FriendsActions.searchUsers({ q }));
      } else {
        this.store.dispatch(FriendsActions.searchCleared());
      }
    });
  }

  onInput(): void { this.input$.next(this.q); }

  send(id: string): void {
    this.store.dispatch(FriendsActions.sendRequest({ addresseeId: id }));
    this.q = '';
    this.store.dispatch(FriendsActions.searchCleared());
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }
}
