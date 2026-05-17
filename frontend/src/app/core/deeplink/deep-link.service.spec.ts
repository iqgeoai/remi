import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { DeepLinkService } from './deep-link.service';

describe('DeepLinkService', () => {
  let svc: DeepLinkService;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    router = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);
    TestBed.configureTestingModule({
      providers: [{ provide: Router, useValue: router }],
    });
    svc = TestBed.inject(DeepLinkService);
  });

  it('routes remi://match/abc123 to /game/abc123', () => {
    svc.handleUrl('remi://match/abc123');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/game/abc123');
  });

  it('routes remi://invite/JOIN42 to /lobby/join-by-code?code=JOIN42', () => {
    svc.handleUrl('remi://invite/JOIN42');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/lobby/join-by-code?code=JOIN42');
  });

  it('ignores garbage URLs', () => {
    svc.handleUrl('https://example.com/foo');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });
});
