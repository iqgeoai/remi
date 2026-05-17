import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { LobbyApi } from './lobby.api';
import { API_URL } from '../config/api-url.tokens';

describe('LobbyApi', () => {
  let api: LobbyApi;
  let httpMock: HttpTestingController;
  const base = '/api';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        LobbyApi,
        { provide: API_URL, useValue: '/api' },
      ],
    });
    api = TestBed.inject(LobbyApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('create POSTs to /games', () => {
    const body = { visibility: 'PRIVATE' as const, numPlayers: 3, mode: 'ETALAT' as const, difficulty: 'MED' as const };
    api.create(body).subscribe();
    const req = httpMock.expectOne(`${base}/games`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('joinByCode POSTs to /games/join-by-code', () => {
    api.joinByCode('ABC12345').subscribe();
    const req = httpMock.expectOne(`${base}/games/join-by-code`);
    expect(req.request.body).toEqual({ joinCode: 'ABC12345' });
    req.flush({});
  });

  it('joinPublic POSTs to /games/{id}/join', () => {
    api.joinPublic('g-1').subscribe();
    const req = httpMock.expectOne(`${base}/games/g-1/join`);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('listPublic GETs /games/public', () => {
    api.listPublic().subscribe();
    const req = httpMock.expectOne(`${base}/games/public`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('myGames GETs /games/mine', () => {
    api.myGames().subscribe();
    httpMock.expectOne(`${base}/games/mine`).flush([]);
  });

  it('get GETs /games/{id}', () => {
    api.get('g-1').subscribe();
    httpMock.expectOne(`${base}/games/g-1`).flush({});
  });

  it('apply POSTs action wrapped in {action}', () => {
    api.apply('g-1', { type: 'DRAW_FROM_STOCK', playerIdx: 0 }).subscribe();
    const req = httpMock.expectOne(`${base}/games/g-1/actions`);
    expect(req.request.body).toEqual({ action: { type: 'DRAW_FROM_STOCK', playerIdx: 0 } });
    req.flush({});
  });

  it('leave POSTs to /games/{id}/leave', () => {
    api.leave('g-1').subscribe();
    httpMock.expectOne(`${base}/games/g-1/leave`).flush(null);
  });
});
