import { TestBed } from '@angular/core/testing';
import { StompService } from './stomp.service';

// Lightweight Client mock injected via Object.defineProperty (we don't try to test reconnect/network — that's covered by manual smoke test)
describe('StompService', () => {
  let service: StompService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
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
