import { Injectable, inject } from '@angular/core';
import { Store } from '@ngrx/store';
import { StompService } from './stomp.service';
import { ChatActions } from '../../store/chat/chat.actions';
import { ChatMessage, dmKey, matchKey } from '../../store/chat/chat.models';

/**
 * Lazy WS bridge: subscribes to match/DM topics on demand when components
 * call {@link subscribeMatch} or {@link subscribeDm}. {@link StompService.subscribe}
 * caches and re-subscribes after reconnect, so each topic is subscribed at most once.
 */
@Injectable({ providedIn: 'root' })
export class ChatWsBridge {
  private readonly stomp = inject(StompService);
  private readonly store = inject(Store);
  private readonly subbedMatches = new Set<string>();
  private readonly subbedDmsByOther = new Set<string>();

  subscribeMatch(matchId: string): void {
    if (this.subbedMatches.has(matchId)) return;
    this.subbedMatches.add(matchId);
    this.stomp.subscribe<ChatMessage>(`/topic/chat/match/${matchId}`).subscribe(msg => {
      this.store.dispatch(
        ChatActions.messageReceived({ channelKey: matchKey(matchId), message: msg }),
      );
    });
  }

  subscribeDm(otherUserId: string): void {
    if (this.subbedDmsByOther.has(otherUserId)) return;
    this.subbedDmsByOther.add(otherUserId);
    this.stomp.subscribe<ChatMessage>(`/user/queue/dm/${otherUserId}`).subscribe(msg => {
      this.store.dispatch(
        ChatActions.messageReceived({ channelKey: dmKey(otherUserId), message: msg }),
      );
    });
  }
}
