import { TestBed } from '@angular/core/testing';
import { StompService } from './stomp.service';
import { WS_URL } from '../config/api-url.tokens';

// Lightweight Client mock injected via Object.defineProperty (we don't try to test reconnect/network — that's covered by manual smoke test)
describe('StompService', () => {
  let service: StompService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: WS_URL, useValue: '/ws' }],
    });
    service = TestBed.inject(StompService);
  });

  it('starts in DISCONNECTED state', (done) => {
    service.connectionState$.subscribe(state => {
      expect(state).toBe('DISCONNECTED');
      done();
    });
  });

  it('subscribe registers an observable even before CONNECT', () => {
    const obs = service.subscribe<{ x: number }>('/topic/test');
    expect(obs).toBeDefined();
  });

  it('send throws when not connected', () => {
    expect(() => service.send('/app/test', { a: 1 })).toThrowError('STOMP not connected');
  });

  it('disconnect resets state', (done) => {
    service.disconnect();
    service.connectionState$.subscribe(state => {
      expect(state).toBe('DISCONNECTED');
      done();
    });
  });
});
