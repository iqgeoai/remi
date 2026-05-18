import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonInput,
  IonItem,
  IonLabel,
  IonList,
  IonTitle,
  IonToolbar,
} from '@ionic/angular/standalone';

import { ChatActions } from '../../store/chat/chat.actions';
import { chatFeature } from '../../store/chat/chat.reducer';
import { ChatMessage, dmKey } from '../../store/chat/chat.models';
import { ChatWsBridge } from '../../core/ws/chat-ws.bridge';

/**
 * Single-thread DM view. Loads history on init, subscribes to the user's
 * personal DM queue for the peer via {@link ChatWsBridge}, dispatches sends.
 */
@Component({
  selector: 'app-dm-thread',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    IonBackButton,
    IonButton,
    IonButtons,
    IonContent,
    IonHeader,
    IonInput,
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
          <ion-back-button defaultHref="/friends/dm"></ion-back-button>
        </ion-buttons>
        <ion-title>Conversație</ion-title>
      </ion-toolbar>
    </ion-header>
    <ion-content>
      <ion-list>
        <ion-item *ngFor="let m of messages$ | async" lines="none">
          <ion-label>
            <strong>{{ m.senderUsername }}:</strong> {{ m.body }}
          </ion-label>
        </ion-item>
      </ion-list>
      <div class="composer">
        <ion-input
          [(ngModel)]="draft"
          placeholder="Mesaj..."
          (keyup.enter)="send()"
        ></ion-input>
        <ion-button size="small" (click)="send()">Trimite</ion-button>
      </div>
    </ion-content>
  `,
  styles: [
    `
      .composer {
        display: flex;
        gap: 4px;
        padding: 8px;
        position: sticky;
        bottom: 0;
        background: var(--ion-background-color);
      }
    `,
  ],
})
export default class DmThreadPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly store = inject(Store);
  private readonly bridge = inject(ChatWsBridge);

  otherUserId = '';
  draft = '';
  messages$!: Observable<ChatMessage[]>;

  ngOnInit(): void {
    this.otherUserId = this.route.snapshot.paramMap.get('otherUserId') ?? '';
    this.store.dispatch(ChatActions.loadDmHistory({ otherUserId: this.otherUserId }));
    this.bridge.subscribeDm(this.otherUserId);
    this.messages$ = this.store
      .select(chatFeature.selectMessagesByChannel)
      .pipe(map(m => m[dmKey(this.otherUserId)] ?? []));
  }

  send(): void {
    const body = this.draft.trim();
    if (!body) return;
    this.store.dispatch(
      ChatActions.sendDmMessage({ otherUserId: this.otherUserId, body }),
    );
    this.draft = '';
  }
}
