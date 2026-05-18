import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { Friend, FriendRequest, UserSearchHit } from './friends.models';

export const FriendsActions = createActionGroup({
  source: 'Friends',
  events: {
    'Load Friends': emptyProps(),
    'Friends Loaded': props<{ friends: Friend[] }>(),
    'Friends Load Failed': props<{ error: string }>(),

    'Load Requests': emptyProps(),
    'Requests Loaded': props<{ incoming: FriendRequest[]; outgoing: FriendRequest[] }>(),

    'Search Users': props<{ q: string }>(),
    'Search Results': props<{ hits: UserSearchHit[] }>(),
    'Search Cleared': emptyProps(),

    'Send Request': props<{ addresseeId: string }>(),
    'Request Sent': props<{ id: number }>(),

    'Accept Request': props<{ id: number }>(),
    'Reject Request': props<{ id: number }>(),
    'Cancel Request': props<{ id: number }>(),
    'Unfriend': props<{ friendId: string }>(),

    'Block User': props<{ userId: string }>(),
    'Unblock User': props<{ userId: string }>(),

    'Presence Updated': props<{ userId: string; online: boolean; since?: string }>(),
    'Friend Request Received': props<{ requestId: number; fromUserId: string; fromUsername: string }>(),
    'Friend Request Accepted': props<{ requestId: number }>(),
  },
});
