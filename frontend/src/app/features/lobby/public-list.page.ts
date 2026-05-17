import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Store } from '@ngrx/store';
import { IonContent, IonList, IonItem, IonLabel, IonButton, IonRefresher, IonRefresherContent }
    from '@ionic/angular/standalone';
import { Lobby } from '../../store/lobby/lobby.actions';
import { selectPublicGames, selectLobbyError, selectLobbyLoading } from '../../store/lobby/lobby.selectors';
import { ErrorBannerComponent } from '../../shared/error-banner/error-banner.component';

@Component({
  selector: 'app-public-list',
  standalone: true,
  imports: [
    CommonModule,
    IonContent, IonList, IonItem, IonLabel, IonButton, IonRefresher, IonRefresherContent,
    ErrorBannerComponent,
  ],
  templateUrl: './public-list.page.html',
})
export default class PublicListPage implements OnInit {
  private readonly store = inject(Store);

  readonly games$ = this.store.select(selectPublicGames);
  readonly error$ = this.store.select(selectLobbyError);
  readonly loading$ = this.store.select(selectLobbyLoading);

  ngOnInit(): void { this.refresh(); }

  refresh(refresher?: CustomEvent): void {
    this.store.dispatch(Lobby.listPublicRequested());
    if (refresher) (refresher.target as HTMLIonRefresherElement).complete();
  }

  join(gameId: string): void {
    this.store.dispatch(Lobby.joinPublicRequested({ gameId }));
  }
}
