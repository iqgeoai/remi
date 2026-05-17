import { TestBed } from '@angular/core/testing';
import { provideMockActions } from '@ngrx/effects/testing';
import { Observable, of } from 'rxjs';
import { Action } from '@ngrx/store';
import { Router } from '@angular/router';
import { LobbyEffects } from './lobby.effects';
import { Lobby } from './lobby.actions';
import { LobbyApi } from '../../core/api/lobby.api';

describe('LobbyEffects', () => {
  let actions$: Observable<Action>;
  let effects: LobbyEffects;
  let apiSpy: jasmine.SpyObj<LobbyApi>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    apiSpy = jasmine.createSpyObj('LobbyApi', ['create','joinByCode','joinPublic','listPublic','myGames','leave']);
    routerSpy = jasmine.createSpyObj('Router', ['navigateByUrl']);
    TestBed.configureTestingModule({
      providers: [
        LobbyEffects,
        provideMockActions(() => actions$),
        { provide: LobbyApi, useValue: apiSpy },
        { provide: Router, useValue: routerSpy },
      ],
    });
    effects = TestBed.inject(LobbyEffects);
  });

  it('create success → createSucceeded', (done) => {
    const game = { id: 'g1' } as any;
    apiSpy.create.and.returnValue(of(game));
    actions$ = of(Lobby.createRequested({ req: {} as any }));
    effects.create$.subscribe(action => {
      expect(action).toEqual(Lobby.createSucceeded({ game }));
      done();
    });
  });
});
