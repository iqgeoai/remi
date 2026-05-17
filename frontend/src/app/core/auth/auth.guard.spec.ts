import { TestBed } from '@angular/core/testing';
import { provideMockStore } from '@ngrx/store/testing';
import { Router, UrlTree } from '@angular/router';
import { firstValueFrom, isObservable } from 'rxjs';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideMockStore({ initialState: { auth: { user: null, tokens: null, status: 'anonymous', error: null, lastInvalidationReason: null } } }),
        { provide: Router, useValue: { parseUrl: (url: string) => ({ toString: () => url } as UrlTree) } },
      ],
    });
  });

  it('returns UrlTree to /login when anonymous', async () => {
    const result = TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));
    if (isObservable(result)) {
      const v = await firstValueFrom(result);
      expect((v as UrlTree).toString()).toBe('/login');
    } else {
      fail('expected Observable');
    }
  });
});
