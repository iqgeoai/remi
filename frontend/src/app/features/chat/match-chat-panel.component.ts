import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnInit,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import {
  IonButton,
  IonInput,
  IonItem,
  IonLabel,
  IonList,
} from '@ionic/angular/standalone';

import { ChatActions } from '../../store/chat/chat.actions';
import { chatFeature } from '../../store/chat/chat.reducer';
import { ChatMessage, matchKey } from '../../store/chat/chat.models';
import { ChatWsBridge } from '../../core/ws/chat-ws.bridge';

/**
 * Floating in-game chat drawer. Loads match history on init, subscribes to
 * the match's WS topic via {@link ChatWsBridge}, and dispatches send actions
 * for outgoing messages. Anchored bottom-right with a toggle button.
 */
@Component({
  selector: 'app-match-chat-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    IonButton,
    IonInput,
    IonItem,
    IonLabel,
    IonList,
  ],
  template: `
    <div class="chat-panel" [class.open]="open">
      <button class="toggle" (click)="open = !open" type="button">
        {{ open ? 'Închide ▼' : 'Chat 💬' }}
      </button>
      <div *ngIf="open" class="body">
        <ion-list class="messages">
          <ion-item *ngFor="let m of messages$ | async" lines="none">
            <ion-label>
              <strong>{{ m.senderUsername }}:</strong> {{ m.body }}
            </ion-label>
          </ion-item>
        </ion-list>
        <div class="input-row">
          <ion-input
            [(ngModel)]="draft"
            placeholder="Mesaj..."
            (keyup.enter)="send()"
          ></ion-input>
          <ion-button size="small" (click)="send()">Trimite</ion-button>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      .chat-panel {
        position: fixed;
        bottom: 0;
        right: 8px;
        width: 320px;
        max-height: 60vh;
        background: rgba(0, 0, 0, 0.85);
        color: white;
        z-index: 1000;
        border-radius: 12px 12px 0 0;
        display: flex;
        flex-direction: column;
      }
      .toggle {
        padding: 8px 16px;
        background: transparent;
        color: white;
        border: none;
        cursor: pointer;
        text-align: left;
        font-weight: 600;
      }
      .body {
        padding: 8px;
        display: flex;
        flex-direction: column;
        gap: 8px;
      }
      ion-list.messages {
        background: transparent;
        max-height: 40vh;
        overflow-y: auto;
      }
      .input-row {
        display: flex;
        gap: 4px;
      }
    `,
  ],
})
export class MatchChatPanelComponent implements OnInit {
  @Input({ required: true }) matchId!: string;

  private readonly store = inject(Store);
  private readonly bridge = inject(ChatWsBridge);

  open = false;
  draft = '';
  messages$!: Observable<ChatMessage[]>;

  ngOnInit(): void {
    this.store.dispatch(ChatActions.loadMatchHistory({ matchId: this.matchId }));
    this.bridge.subscribeMatch(this.matchId);
    this.messages$ = this.store
      .select(chatFeature.selectMessagesByChannel)
      .pipe(map(m => m[matchKey(this.matchId)] ?? []));
  }

  send(): void {
    const body = this.draft.trim();
    if (!body) return;
    this.store.dispatch(
      ChatActions.sendMatchMessage({ matchId: this.matchId, body }),
    );
    this.draft = '';
  }
}
