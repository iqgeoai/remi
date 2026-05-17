import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MatchmakingApi } from './matchmaking.api';
import { environment } from '../../../environments/environment';

describe('MatchmakingApi', () => {
  let api: MatchmakingApi;
  let httpMock: HttpTestingController;
  const base = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), MatchmakingApi],
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
