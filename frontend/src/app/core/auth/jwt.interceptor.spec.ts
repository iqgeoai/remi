import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideMockStore } from '@ngrx/store/testing';
import { Store } from '@ngrx/store';
import { jwtInterceptor } from './jwt.interceptor';
import { AuthStorageService } from './auth-storage.service';
import { AuthApi } from '../api/auth.api';
import { Auth } from '../../store/auth/auth.actions';

describe('jwtInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let storage: jasmine.SpyObj<AuthStorageService>;
  let api: jasmine.SpyObj<AuthApi>;
  let store: jasmine.SpyObj<Store>;

  beforeEach(() => {
    storage = jasmine.createSpyObj('AuthStorageService', ['getTokens', 'setTokens', 'clear']);
    api = jasmine.createSpyObj('AuthApi', ['refresh']);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])),
        provideHttpClientTesting(),
        provideMockStore({ initialState: {} }),
        { provide: AuthStorageService, useValue: storage },
        { provide: AuthApi, useValue: api },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    store = TestBed.inject(Store) as jasmine.SpyObj<Store>;
    spyOn(store, 'dispatch');
  });

  afterEach(() => httpMock.verify());

  it('adds Bearer header when tokens present', () => {
    storage.getTokens.and.returnValue({ accessToken: 'access-1', refreshToken: 'r', accessExpiresAt: 'x' });
    http.get('/api/users/me').subscribe();
    const req = httpMock.expectOne('/api/users/me');
    expect(req.request.headers.get('Authorization')).toBe('Bearer access-1');
    req.flush({});
  });

  it('omits Bearer header when no tokens', () => {
    storage.getTokens.and.returnValue(null);
    http.get('/api/auth/login').subscribe();
    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('on 401 dispatches sessionInvalidated', (done) => {
    storage.getTokens.and.returnValue(null);
    http.get('/api/games/mine').subscribe({
      error: () => {
        expect(store.dispatch).toHaveBeenCalledWith(
            Auth.sessionInvalidated({ reason: 'UNAUTHORIZED' }));
        done();
      },
    });
    httpMock.expectOne('/api/games/mine').flush(
        { code: 'UNAUTHORIZED', message: 'x' },
        { status: 401, statusText: 'Unauthorized' });
  });
});
