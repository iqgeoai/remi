import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { provideMockStore, MockStore } from '@ngrx/store/testing';
import { provideRouter } from '@angular/router';
import LoginPage from './login.page';
import { Auth } from '../../store/auth/auth.actions';

describe('LoginPage', () => {
  let fixture: ComponentFixture<LoginPage>;
  let store: MockStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LoginPage, ReactiveFormsModule],
      providers: [
        provideMockStore({ initialState: { auth: { user: null, tokens: null, status: 'anonymous', error: null, lastInvalidationReason: null } } }),
        provideRouter([]),
      ],
    });
    fixture = TestBed.createComponent(LoginPage);
    store = TestBed.inject(MockStore);
    spyOn(store, 'dispatch');
  });

  it('submit with invalid form does not dispatch', () => {
    fixture.detectChanges();
    fixture.componentInstance.submit();
    expect(store.dispatch).not.toHaveBeenCalled();
  });

  it('submit with valid form dispatches loginRequested', () => {
    fixture.componentInstance.form.setValue({ emailOrUsername: 'alice', password: 'passwordxx' });
    fixture.componentInstance.submit();
    expect(store.dispatch).toHaveBeenCalledWith(
        Auth.loginRequested({ emailOrUsername: 'alice', password: 'passwordxx' }));
  });
});
