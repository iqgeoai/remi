import { TestBed } from '@angular/core/testing';
import { StompService } from './stomp.service';
import { GameWsService } from './game-ws.service';
import { Subject } from 'rxjs';

describe('GameWsService', () => {
  let service: GameWsService;
  let stompSpy: jasmine.SpyObj<StompService>;

  beforeEach(() => {
    stompSpy = jasmine.createSpyObj('StompService', ['subscribe', 'send'], {
      connectionState$: new Subject(),
    });
    stompSpy.subscribe.and.returnValue(new Subject());

    TestBed.configureTestingModule({
      providers: [
        GameWsService,
        { provide: StompService, useValue: stompSpy },
      ],
    });
    service = TestBed.inject(GameWsService);
  });

  it('subscribeToGame subscribes to /user/queue/games/{id}', () => {
    service.subscribeToGame('g-1');
    expect(stompSpy.subscribe).toHaveBeenCalledWith('/user/queue/games/g-1');
  });

  it('subscribeToErrors subscribes to /user/queue/errors', () => {
    service.subscribeToErrors();
    expect(stompSpy.subscribe).toHaveBeenCalledWith('/user/queue/errors');
  });

  it('subscribeToMatches subscribes to /user/queue/match', () => {
    service.subscribeToMatches();
    expect(stompSpy.subscribe).toHaveBeenCalledWith('/user/queue/match');
  });

  it('sendAction sends to /app/games/{id}/actions', () => {
    service.sendAction('g-1', { type: 'DRAW_FROM_STOCK', playerIdx: 0 });
    expect(stompSpy.send).toHaveBeenCalledWith('/app/games/g-1/actions',
        { type: 'DRAW_FROM_STOCK', playerIdx: 0 });
  });
});
