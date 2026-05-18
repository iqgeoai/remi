import { createFeature, createReducer, on } from '@ngrx/store';
import { Friend, FriendRequest, UserSearchHit } from './friends.models';
import { FriendsActions } from './friends.actions';

export interface FriendsState {
  friends: Friend[];
  incoming: FriendRequest[];
  outgoing: FriendRequest[];
  searchHits: UserSearchHit[];
  error: string | null;
}

export const initialFriendsState: FriendsState = {
  friends: [],
  incoming: [],
  outgoing: [],
  searchHits: [],
  error: null,
};

export const friendsFeature = createFeature({
  name: 'friends',
  reducer: createReducer(
    initialFriendsState,
    on(FriendsActions.friendsLoaded, (s, { friends }) => ({ ...s, friends, error: null })),
    on(FriendsActions.friendsLoadFailed, (s, { error }) => ({ ...s, error })),
    on(FriendsActions.requestsLoaded, (s, { incoming, outgoing }) => ({ ...s, incoming, outgoing })),
    on(FriendsActions.searchResults, (s, { hits }) => ({ ...s, searchHits: hits })),
    on(FriendsActions.searchCleared, (s) => ({ ...s, searchHits: [] })),
    on(FriendsActions.presenceUpdated, (s, { userId, online, since }) => ({
      ...s,
      friends: s.friends.map(f => f.id === userId ? { ...f, online, since } : f),
    })),
    on(FriendsActions.friendRequestAccepted, (s, { requestId }) => ({
      ...s,
      outgoing: s.outgoing.filter(r => r.id !== requestId),
    })),
  ),
});
