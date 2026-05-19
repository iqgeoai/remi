import { ApplicationConfig, ErrorHandler, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding, withRouterConfig } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { IonicRouteStrategy, provideIonicAngular } from '@ionic/angular/standalone';
import { RouteReuseStrategy } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideStore } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { provideStoreDevtools } from '@ngrx/store-devtools';

import { routes } from './app.routes';
import { jwtInterceptor } from './core/auth/jwt.interceptor';
import { authFeature } from './store/auth/auth.feature';
import { lobbyFeature } from './store/lobby/lobby.reducer';
import { matchFeature } from './store/match/match.reducer';
import { gameFeature } from './store/game/game.reducer';
import { friendsFeature } from './store/friends/friends.reducer';
import { chatFeature } from './store/chat/chat.reducer';
import { statsFeature } from './store/stats/stats.reducer';
import { AuthEffects } from './store/auth/auth.effects';
import { AuthWsEffects } from './store/auth/auth-ws.effects';
import { LobbyEffects } from './store/lobby/lobby.effects';
import { MatchEffects } from './store/match/match.effects';
import { GameEffects } from './store/game/game.effects';
import { FriendsEffects } from './store/friends/friends.effects';
import { ChatEffects } from './store/chat/chat.effects';
import { StatsEffects } from './store/stats/stats.effects';
import { GlobalErrorHandler } from './shared/global-error-handler';
import { provideApiUrls } from './core/config/api-url.provider';
import { environment } from '../environments/environment';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    { provide: RouteReuseStrategy, useClass: IonicRouteStrategy },
    provideIonicAngular(),
    provideAnimations(),
    provideRouter(routes, withRouterConfig({ paramsInheritanceStrategy: 'always' }), withComponentInputBinding()),
    provideHttpClient(withInterceptors([jwtInterceptor])),
    provideStore({
      [authFeature.name]: authFeature.reducer,
      [lobbyFeature.name]: lobbyFeature.reducer,
      [matchFeature.name]: matchFeature.reducer,
      [gameFeature.name]: gameFeature.reducer,
      [friendsFeature.name]: friendsFeature.reducer,
      [chatFeature.name]: chatFeature.reducer,
      [statsFeature.name]: statsFeature.reducer,
    }),
    provideEffects(AuthEffects, AuthWsEffects, LobbyEffects, MatchEffects, GameEffects, FriendsEffects, ChatEffects, StatsEffects),
    provideStoreDevtools({ maxAge: 25, logOnly: environment.production }),
    { provide: ErrorHandler, useClass: GlobalErrorHandler },
    provideApiUrls(),
  ],
};
