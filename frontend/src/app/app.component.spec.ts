import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideMockStore, MockStore } from '@ngrx/store/testing';
import { Subject } from 'rxjs';
import { AppComponent } from './app.component';
import { Auth } from './store/auth/auth.actions';
import { StompService } from './core/ws/stomp.service';
import { provideRouter } from '@angular/router';

describe('AppComponent', () => {
  let fixture: ComponentFixture<AppComponent>;
  let store: MockStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideMockStore({ initialState: { auth: { user: null, tokens: null, status: 'anonymous', error: null, lastInvalidationReason: null } } }),
        provideRouter([]),
        { provide: StompService, useValue: { connectionState$: new Subject() } },
      ],
    });
    fixture = TestBed.createComponent(AppComponent);
    store = TestBed.inject(MockStore);
    spyOn(store, 'dispatch');
  });

  it('dispatches bootstrapFromStorage on init', () => {
    fixture.detectChanges();
    expect(store.dispatch).toHaveBeenCalledWith(Auth.bootstrapFromStorage());
  });

  it('logout dispatches Auth.logoutRequested', () => {
    fixture.componentInstance.logout();
    expect(store.dispatch).toHaveBeenCalledWith(Auth.logoutRequested());
  });
});
