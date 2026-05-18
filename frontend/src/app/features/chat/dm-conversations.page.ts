import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import {
  IonBackButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonItem,
  IonLabel,
  IonList,
  IonTitle,
  IonToolbar,
} from '@ionic/angular/standalone';

import { ChatActions } from '../../store/chat/chat.actions';
import { chatFeature } from '../../store/chat/chat.reducer';

/**
 * Lists the current user's DM conversations sorted by recency. Each row
 * deep-links to {@code /friends/dm/:otherUserId} which opens the thread.
 */
@Component({
  selector: 'app-dm-conversations',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    RouterLink,
    IonBackButton,
    IonButtons,
    IonContent,
    IonHeader,
    IonItem,
    IonLabel,
    IonList,
    IonTitle,
    IonToolbar,
  ],
  template: `
    <ion-header>
      <ion-toolbar>
        <ion-buttons slot="start">
          <ion-back-button defaultHref="/friends"></ion-back-button>
        </ion-buttons>
        <ion-title>Mesaje</ion-title>
      </ion-toolbar>
    </ion-header>
    <ion-content>
      <ion-list>
        <ion-item
          *ngFor="let c of conversations$ | async"
          button
          [routerLink]="['/friends/dm', c.otherUserId]"
        >
          <ion-label>
            <h2>{{ c.otherUsername }}</h2>
            <p>{{ c.lastBody }}</p>
          </ion-label>
        </ion-item>
      </ion-list>
    </ion-content>
  `,
})
export default class DmConversationsPage implements OnInit {
  private readonly store = inject(Store);
  readonly conversations$ = this.store.select(chatFeature.selectConversations);

  ngOnInit(): void {
    this.store.dispatch(ChatActions.loadConversations());
  }
}
