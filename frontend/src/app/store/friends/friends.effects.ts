import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { from, of } from 'rxjs';
import { catchError, map, mergeMap, switchMap } from 'rxjs/operators';
import { FriendsActions } from './friends.actions';
import { FriendsApi } from '../../core/api/friends.api';

@Injectable()
export class FriendsEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(FriendsApi);

  loadFriends$ = createEffect(() =>
    this.actions$.pipe(
      ofType(FriendsActions.loadFriends),
      switchMap(() => from(this.api.listFriends()).pipe(
        map(friends => FriendsActions.friendsLoaded({ friends })),
        catchError(err => of(FriendsActions.friendsLoadFailed({ error: err?.message ?? 'unknown' }))),
      )),
    ));

  loadRequests$ = createEffect(() =>
    this.actions$.pipe(
      ofType(FriendsActions.loadRequests),
      switchMap(() => from(this.api.listRequests()).pipe(
        map(({ incoming, outgoing }) => FriendsActions.requestsLoaded({ incoming, outgoing })),
      )),
    ));

  search$ = createEffect(() =>
    this.actions$.pipe(
      ofType(FriendsActions.searchUsers),
      switchMap(({ q }) => from(this.api.searchUsers(q)).pipe(
        map(hits => FriendsActions.searchResults({ hits })),
      )),
    ));

  sendRequest$ = createEffect(() =>
    this.actions$.pipe(
      ofType(FriendsActions.sendRequest),
      mergeMap(({ addresseeId }) => from(this.api.sendRequest(addresseeId)).pipe(
        map(({ id }) => FriendsActions.requestSent({ id })),
      )),
    ));

  accept$ = createEffect(() =>
    this.actions$.pipe(
      ofType(FriendsActions.acceptRequest),
      mergeMap(({ id }) => from(this.api.accept(id)).pipe(
        map(() => FriendsActions.loadFriends()),
      )),
    ));

  reject$ = createEffect(() =>
    this.actions$.pipe(
      ofType(FriendsActions.rejectRequest),
      mergeMap(({ id }) => from(this.api.reject(id)).pipe(
        map(() => FriendsActions.loadRequests()),
      )),
    ));

  cancel$ = createEffect(() =>
    this.actions$.pipe(
      ofType(FriendsActions.cancelRequest),
      mergeMap(({ id }) => from(this.api.cancel(id)).pipe(
        map(() => FriendsActions.loadRequests()),
      )),
    ));

  unfriend$ = createEffect(() =>
    this.actions$.pipe(
      ofType(FriendsActions.unfriend),
      mergeMap(({ friendId }) => from(this.api.unfriend(friendId)).pipe(
        map(() => FriendsActions.loadFriends()),
      )),
    ));

  block$ = createEffect(() =>
    this.actions$.pipe(
      ofType(FriendsActions.blockUser),
      mergeMap(({ userId }) => from(this.api.block(userId)).pipe(
        map(() => FriendsActions.loadFriends()),
      )),
    ));

  unblock$ = createEffect(() =>
    this.actions$.pipe(
      ofType(FriendsActions.unblockUser),
      mergeMap(({ userId }) => from(this.api.unblock(userId)).pipe(
        map(() => FriendsActions.loadFriends()),
      )),
    ));
}
