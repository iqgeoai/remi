import { Injectable, OnDestroy, inject } from '@angular/core';
import { Store } from '@ngrx/store';
import { Subscription } from 'rxjs';
import { StompService } from './stomp.service';
import { FriendsActions } from '../../store/friends/friends.actions';

interface PresenceMessage {
  userId: string;
  online: boolean;
  since?: string;
}

interface FriendRequestMessage {
  type: 'incoming' | 'accepted';
  requestId: number;
  fromUserId?: string;
  fromUsername?: string;
}

/**
 * Bridges WS topics ({@code /user/queue/presence} and {@code /user/queue/friend-requests})
 * into NgRx by translating each incoming message into a {@link FriendsActions}
 * dispatch. {@link StompService.subscribe} buffers subscription intent and
 * re-subscribes after every (re)connect, so {@link start} is safe to call once
 * during app bootstrap regardless of current connection state.
 */
@Injectable({ providedIn: 'root' })
export class FriendsWsBridge implements OnDestroy {
  private readonly stomp = inject(StompService);
  private readonly store = inject(Store);
  private readonly subs: Subscription[] = [];
  private started = false;

  start(): void {
    if (this.started) return;
    this.started = true;
    this.subs.push(
      this.stomp.subscribe<PresenceMessage>('/user/queue/presence').subscribe(msg => {
        this.store.dispatch(FriendsActions.presenceUpdated({
          userId: msg.userId,
          online: msg.online,
          since: msg.since,
        }));
      }),
    );
    this.subs.push(
      this.stomp.subscribe<FriendRequestMessage>('/user/queue/friend-requests').subscribe(msg => {
        if (msg.type === 'incoming' && msg.fromUserId && msg.fromUsername) {
          this.store.dispatch(FriendsActions.friendRequestReceived({
            requestId: msg.requestId,
            fromUserId: msg.fromUserId,
            fromUsername: msg.fromUsername,
          }));
          this.store.dispatch(FriendsActions.loadRequests());
        } else if (msg.type === 'accepted') {
          this.store.dispatch(FriendsActions.friendRequestAccepted({ requestId: msg.requestId }));
          this.store.dispatch(FriendsActions.loadFriends());
        }
      }),
    );
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
    this.subs.length = 0;
  }
}
