import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'lobby', pathMatch: 'full' },
  { path: 'login', loadComponent: () => import('./features/auth/login.page') },
  { path: 'register', loadComponent: () => import('./features/auth/register.page') },
  { path: 'verify', loadComponent: () => import('./features/auth/verify-email.page') },
  { path: 'reset/request', loadComponent: () => import('./features/auth/request-reset.page') },
  { path: 'reset/confirm', loadComponent: () => import('./features/auth/reset-password.page') },
  {
    path: 'lobby',
    canActivate: [authGuard],
    children: [
      { path: '', loadComponent: () => import('./features/lobby/lobby-home.page') },
      { path: 'create', loadComponent: () => import('./features/lobby/create-game.page') },
      { path: 'join', loadComponent: () => import('./features/lobby/join-by-code.page') },
      { path: 'public', loadComponent: () => import('./features/lobby/public-list.page') },
      { path: 'quick', loadComponent: () => import('./features/lobby/quick-match.page') },
    ],
  },
  { path: 'game/:id', canActivate: [authGuard],
    loadComponent: () => import('./features/game/game.page') },
  { path: 'profile/:id', canActivate: [authGuard],
    loadComponent: () => import('./features/stats/profile.page').then(m => m.ProfilePage) },
  { path: 'leaderboard', canActivate: [authGuard],
    loadComponent: () => import('./features/stats/leaderboard.page').then(m => m.LeaderboardPage) },
  {
    path: 'friends',
    canActivate: [authGuard],
    children: [
      { path: '', loadComponent: () => import('./features/friends/friends-home.page') },
      { path: 'search', loadComponent: () => import('./features/friends/friend-search.page') },
      { path: 'requests', loadComponent: () => import('./features/friends/friend-requests.page') },
      { path: 'blocked', loadComponent: () => import('./features/friends/blocked-list.page') },
      { path: 'dm', loadComponent: () => import('./features/chat/dm-conversations.page') },
      { path: 'dm/:otherUserId', loadComponent: () => import('./features/chat/dm-thread.page') },
    ],
  },
  { path: '**', redirectTo: 'lobby' },
];
