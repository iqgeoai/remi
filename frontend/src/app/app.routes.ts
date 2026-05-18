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
  {
    path: 'friends',
    canActivate: [authGuard],
    loadComponent: () => import('./features/friends/friends-home.page'),
  },
  { path: '**', redirectTo: 'lobby' },
];
