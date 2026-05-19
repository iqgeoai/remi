import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonList, IonItem, IonLabel,
  IonBackButton, IonButtons, IonBadge,
} from '@ionic/angular/standalone';
import { StatsActions } from '../../store/stats/stats.actions';
import { statsFeature } from '../../store/stats/stats.reducer';

@Component({
  selector: 'app-leaderboard',
  standalone: true,
  imports: [
    CommonModule, RouterLink,
    IonContent, IonHeader, IonToolbar, IonTitle, IonList, IonItem, IonLabel,
    IonBackButton, IonButtons, IonBadge,
  ],
  template: `
    <ion-header>
      <ion-toolbar>
        <ion-buttons slot="start"><ion-back-button></ion-back-button></ion-buttons>
        <ion-title>Clasament</ion-title>
      </ion-toolbar>
    </ion-header>
    <ion-content>
      <ion-list>
        <ion-item *ngFor="let e of (entries$ | async); let i = index" [routerLink]="['/profile', e.id]">
          <ion-label>
            <h2>#{{ i + 1 }} {{ e.username }}</h2>
            <p>{{ e.totalMatches }} meciuri, {{ e.wins }} câștigate</p>
          </ion-label>
          <ion-badge slot="end">{{ e.rating }}</ion-badge>
        </ion-item>
      </ion-list>
    </ion-content>
  `,
})
export class LeaderboardPage implements OnInit {
  private readonly store = inject(Store);
  readonly entries$ = this.store.select(statsFeature.selectLeaderboard);

  ngOnInit(): void {
    this.store.dispatch(StatsActions.loadLeaderboard());
  }
}
