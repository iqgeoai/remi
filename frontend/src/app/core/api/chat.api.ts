import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../config/api-url.tokens';
import { ChatMessage, DmConversation } from '../../store/chat/chat.models';

@Injectable({ providedIn: 'root' })
export class ChatApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_URL);

  matchHistory(matchId: string): Promise<ChatMessage[]> {
    return firstValueFrom(
      this.http.get<ChatMessage[]>(`${this.base}/chat/match/${matchId}?limit=200`),
    );
  }

  dmHistory(otherUserId: string): Promise<ChatMessage[]> {
    return firstValueFrom(
      this.http.get<ChatMessage[]>(`${this.base}/chat/dm/${otherUserId}?limit=200`),
    );
  }

  sendMatch(matchId: string, body: string): Promise<{ id: number }> {
    return firstValueFrom(
      this.http.post<{ id: number }>(`${this.base}/chat/match/${matchId}`, { body }),
    );
  }

  sendDm(otherUserId: string, body: string): Promise<{ id: number }> {
    return firstValueFrom(
      this.http.post<{ id: number }>(`${this.base}/chat/dm/${otherUserId}`, { body }),
    );
  }

  conversations(): Promise<DmConversation[]> {
    return firstValueFrom(
      this.http.get<DmConversation[]>(`${this.base}/chat/dm/conversations`),
    );
  }
}
