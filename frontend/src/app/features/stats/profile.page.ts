import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { Store } from '@ngrx/store';
import {
  IonContent, IonHeader, IonToolbar, IonTitle, IonCard, IonCardHeader,
  IonCardTitle, IonCardContent, IonList, IonItem, IonLabel, IonBackButton,
  IonButtons,
} from '@ionic/angular/standalone';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { StatsActions } from '../../store/stats/stats.actions';
import { statsFeature } from '../../store/stats/stats.reducer';
import { Profile } from '../../store/stats/stats.models';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [
    CommonModule,
    IonContent, IonHeader, IonToolbar, IonTitle, IonCard, IonCardHeader,
    IonCardTitle, IonCardContent, IonList, IonItem, IonLabel, IonBackButton,
    IonButtons,
  ],
  template: `
    <ion-header>
      <ion-toolbar>
        <ion-buttons slot="start"><ion-back-button></ion-back-button></ion-buttons>
        <ion-title>Profil</ion-title>
      </ion-toolbar>
    </ion-header>
    <ion-content>
      <ng-container *ngIf="(profile$ | async) as p">
        <ion-card>
          <ion-card-header><ion-card-title>{{ p.username }}</ion-card-title></ion-card-header>
          <ion-card-content>
            <p><strong>Rating:</strong> {{ p.rating }}</p>
            <p><strong>Meciuri:</strong> {{ p.totalMatches }} ({{ p.wins }}W / {{ p.losses }}L)</p>
            <p><strong>Rată câștig:</strong> {{ (p.winRate * 100).toFixed(1) }}%</p>
            <p><strong>Puncte totale:</strong> {{ p.totalPoints }}</p>
          </ion-card-content>
        </ion-card>
        <ion-list>
          <ion-item *ngFor="let m of p.recentMatches">
            <ion-label>
              <h2>vs {{ m.winnerUsername }} ({{ m.rank === 1 ? 'win' : 'loss' }})</h2>
              <p>Scor: {{ m.score }} | ΔRating: {{ m.ratingDelta > 0 ? '+' : '' }}{{ m.ratingDelta }} | {{ m.finishedAt | date:'short' }}</p>
            </ion-label>
          </ion-item>
        </ion-list>
      </ng-container>
    </ion-content>
  `,
})
export class ProfilePage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly store = inject(Store);
  profile$!: Observable<Profile | null>;
  userId = '';

  ngOnInit(): void {
    this.userId = this.route.snapshot.paramMap.get('id')!;
    this.store.dispatch(StatsActions.loadProfile({ userId: this.userId }));
    this.profile$ = this.store
      .select(statsFeature.selectProfiles)
      .pipe(map(ps => ps[this.userId] ?? null));
  }
}
