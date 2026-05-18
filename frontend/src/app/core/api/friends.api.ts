import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { API_URL } from '../config/api-url.tokens';
import { Friend, FriendRequest, UserSearchHit } from '../../store/friends/friends.models';

interface RequestsResponse {
  incoming: FriendRequest[];
  outgoing: FriendRequest[];
}

export interface InviteSettings {
  numPlayers?: number;
  mode?: 'ETALAT' | 'TABLA';
  difficulty?: 'EASY' | 'MED' | 'HARD';
}

export interface InviteResult {
  code: string;
  matchId: string;
}

@Injectable({ providedIn: 'root' })
export class FriendsApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_URL);

  listFriends(): Promise<Friend[]> {
    return firstValueFrom(this.http.get<Friend[]>(`${this.base}/friends`));
  }
  listRequests(): Promise<RequestsResponse> {
    return firstValueFrom(this.http.get<RequestsResponse>(`${this.base}/friends/requests`));
  }
  searchUsers(q: string): Promise<UserSearchHit[]> {
    return firstValueFrom(
      this.http.get<UserSearchHit[]>(`${this.base}/users/search?q=${encodeURIComponent(q)}`),
    );
  }
  sendRequest(addresseeId: string): Promise<{ id: number }> {
    return firstValueFrom(
      this.http.post<{ id: number }>(`${this.base}/friends/requests`, { addresseeId }),
    );
  }
  accept(id: number): Promise<void> {
    return firstValueFrom(
      this.http.post<void>(`${this.base}/friends/requests/${id}/accept`, {}),
    ).then(() => undefined);
  }
  reject(id: number): Promise<void> {
    return firstValueFrom(
      this.http.post<void>(`${this.base}/friends/requests/${id}/reject`, {}),
    ).then(() => undefined);
  }
  cancel(id: number): Promise<void> {
    return firstValueFrom(
      this.http.delete<void>(`${this.base}/friends/requests/${id}`),
    ).then(() => undefined);
  }
  unfriend(friendId: string): Promise<void> {
    return firstValueFrom(
      this.http.delete<void>(`${this.base}/friends/${friendId}`),
    ).then(() => undefined);
  }
  block(userId: string): Promise<void> {
    return firstValueFrom(
      this.http.post<void>(`${this.base}/users/${userId}/block`, {}),
    ).then(() => undefined);
  }
  unblock(userId: string): Promise<void> {
    return firstValueFrom(
      this.http.delete<void>(`${this.base}/users/${userId}/block`),
    ).then(() => undefined);
  }
  invite(friendId: string, settings: InviteSettings = {}): Promise<InviteResult> {
    return firstValueFrom(
      this.http.post<InviteResult>(`${this.base}/friends/${friendId}/invite`, settings),
    );
  }
}
