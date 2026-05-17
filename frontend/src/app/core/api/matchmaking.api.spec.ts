import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MatchmakingApi } from './matchmaking.api';
import { API_URL } from '../config/api-url.tokens';

describe('MatchmakingApi', () => {
  let api: MatchmakingApi;
  let httpMock: HttpTestingController;
  const base = '/api';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        MatchmakingApi,
        { provide: API_URL, useValue: '/api' },
      ],
    });
    api = TestBed.inject(MatchmakingApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('quick POSTs to /matchmaking/quick', () => {
    api.quick({ numPlayers: 2, mode: 'ETALAT', difficulty: 'MED' }).subscribe();
    const req = httpMock.expectOne(`${base}/matchmaking/quick`);
    expect(req.request.method).toBe('POST');
    req.flush({ matched: false });
  });

  it('cancel POSTs to /matchmaking/cancel', () => {
    api.cancel().subscribe();
    const req = httpMock.expectOne(`${base}/matchmaking/cancel`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });
});
