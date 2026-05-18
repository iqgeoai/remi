import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideMockStore, MockStore } from '@ngrx/store/testing';
import { Subject } from 'rxjs';
import { AppComponent } from './app.component';
import { Auth } from './store/auth/auth.actions';
import { StompService } from './core/ws/stomp.service';
import { AuthStorageService } from './core/auth/auth-storage.service';
import { DeepLinkService } from './core/deeplink/deep-link.service';
import { provideRouter } from '@angular/router';

describe('AppComponent', () => {
  let fixture: ComponentFixture<AppComponent>;
  let store: MockStore;
  let authStorage: jasmine.SpyObj<AuthStorageService>;
  let deepLink: jasmine.SpyObj<DeepLinkService>;

  beforeEach(() => {
    authStorage = jasmine.createSpyObj<AuthStorageService>('AuthStorageService',
      ['migrateLegacyToken', 'getTokens', 'setTokens', 'clear']);
    authStorage.migrateLegacyToken.and.resolveTo();
    deepLink = jasmine.createSpyObj<DeepLinkService>('DeepLinkService',
      ['init', 'handleUrl']);
    deepLink.init.and.resolveTo();
    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideMockStore({ initialState: { auth: { user: null, tokens: null, status: 'anonymous', error: null, lastInvalidationReason: null } } }),
        provideRouter([]),
        { provide: StompService, useValue: { connectionState$: new Subject() } },
        { provide: AuthStorageService, useValue: authStorage },
        { provide: DeepLinkService, useValue: deepLink },
      ],
    });
    fixture = TestBed.createComponent(AppComponent);
    store = TestBed.inject(MockStore);
    spyOn(store, 'dispatch');
  });

  it('dispatches bootstrapFromStorage on init after migration', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    expect(authStorage.migrateLegacyToken).toHaveBeenCalled();
    expect(store.dispatch).toHaveBeenCalledWith(Auth.bootstrapFromStorage());
  });

  it('logout dispatches Auth.logoutRequested', () => {
    fixture.componentInstance.logout();
    expect(store.dispatch).toHaveBeenCalledWith(Auth.logoutRequested());
  });
});
