import { Injectable, inject } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { Observable, Subject, BehaviorSubject } from 'rxjs';
import * as SockJS from 'sockjs-client';
import { WS_URL } from '../config/api-url.tokens';
import { WsConnectionState } from './ws-state';

interface ActiveSubscription<T = unknown> {
  destination: string;
  subject: Subject<T>;
  deserializer: (raw: string) => T;
  stompSub?: StompSubscription;
}

@Injectable({ providedIn: 'root' })
export class StompService {
  private client: Client | null = null;
  private readonly state$ = new BehaviorSubject<WsConnectionState>('DISCONNECTED');
  private readonly activeSubs = new Map<string, ActiveSubscription>();
  private accessToken: string | null = null;
  private readonly url = inject(WS_URL);

  /** Public: observable of connection state. */
  readonly connectionState$ = this.state$.asObservable();

  connect(accessToken: string): Observable<WsConnectionState> {
    this.accessToken = accessToken;
    if (this.client) {
      this.client.deactivate();
    }
    this.state$.next('CONNECTING');
    this.client = new Client({
      webSocketFactory: () => new SockJS(this.url),
      connectHeaders: { Authorization: `Bearer ${accessToken}` },
      reconnectDelay: 1000,           // start at 1s
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        this.state$.next('CONNECTED');
        this.resubscribeAll();
      },
      onDisconnect: () => this.state$.next('DISCONNECTED'),
      onWebSocketClose: () => {
        if (this.state$.value === 'CONNECTED') {
          this.state$.next('RECONNECTING');
        }
      },
      onStompError: () => this.state$.next('DISCONNECTED'),
    });
    this.client.activate();
    return this.connectionState$;
  }

  disconnect(): void {
    this.client?.deactivate();
    this.activeSubs.forEach(s => s.stompSub?.unsubscribe());
    this.activeSubs.clear();
    this.client = null;
    this.state$.next('DISCONNECTED');
  }

  subscribe<T = unknown>(
    destination: string,
    deserializer: (raw: string) => T = JSON.parse as (raw: string) => T,
  ): Observable<T> {
    let entry = this.activeSubs.get(destination) as ActiveSubscription<T> | undefined;
    if (!entry) {
      entry = { destination, subject: new Subject<T>(), deserializer };
      this.activeSubs.set(destination, entry as ActiveSubscription);
      this.subscribeOnClient(entry as ActiveSubscription);
    }
    return entry.subject.asObservable();
  }

  send(destination: string, payload: unknown): void {
    if (!this.client || !this.client.connected) {
      throw new Error('STOMP not connected');
    }
    this.client.publish({
      destination,
      body: JSON.stringify(payload),
      headers: { 'content-type': 'application/json' },
    });
  }

  private subscribeOnClient(entry: ActiveSubscription): void {
    if (!this.client || !this.client.connected) return;
    entry.stompSub = this.client.subscribe(entry.destination, (msg: IMessage) => {
      try {
        const value = entry.deserializer(msg.body);
        entry.subject.next(value);
      } catch (e) {
        entry.subject.error(e);
      }
    });
  }

  private resubscribeAll(): void {
    this.activeSubs.forEach(entry => this.subscribeOnClient(entry));
  }
}
