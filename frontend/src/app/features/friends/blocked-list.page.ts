import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Store } from '@ngrx/store';
import { firstValueFrom } from 'rxjs';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonList, IonItem, IonLabel,
  IonButton,
} from '@ionic/angular/standalone';
import { API_URL } from '../../core/config/api-url.tokens';
import { FriendsActions } from '../../store/friends/friends.actions';

interface BlockedUser {
  id: string;
  username: string;
}

/**
 * Lists users the current account has blocked. Uses {@code GET /api/users/blocked}
 * directly (no NgRx slice for blocked list — it is a low-traffic read-only view).
 * Unblock dispatches the friends action so any other subscribers (search,
 * friends list) react; we also remove the row locally for instant feedback.
 */
@Component({
  selector: 'app-blocked-list',
  standalone: true,
  imports: [
    CommonModule,
    IonContent, IonHeader, IonToolbar, IonTitle, IonList, IonItem, IonLabel,
    IonButton,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ion-header><ion-toolbar><ion-title>Blocați</ion-title></ion-toolbar></ion-header>
    <ion-content class="ion-padding">
      <ion-list>
        <ion-item *ngFor="let b of blocked">
          <ion-label>{{ b.username }}</ion-label>
          <ion-button slot="end" (click)="unblock(b.id)">Deblochează</ion-button>
        </ion-item>
      </ion-list>
    </ion-content>
  `,
})
export default class BlockedListPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_URL);
  private readonly store = inject(Store);
  private readonly cdr = inject(ChangeDetectorRef);
  blocked: BlockedUser[] = [];

  async ngOnInit(): Promise<void> {
    this.blocked = await firstValueFrom(
      this.http.get<BlockedUser[]>(`${this.base}/users/blocked`),
    );
    this.cdr.markForCheck();
  }

  unblock(id: string): void {
    this.store.dispatch(FriendsActions.unblockUser({ userId: id }));
    this.blocked = this.blocked.filter(b => b.id !== id);
    this.cdr.markForCheck();
  }
}
