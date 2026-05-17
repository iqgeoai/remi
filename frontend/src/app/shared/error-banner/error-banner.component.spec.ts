import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ErrorBannerComponent } from './error-banner.component';

describe('ErrorBannerComponent', () => {
  let fixture: ComponentFixture<ErrorBannerComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ErrorBannerComponent] });
    fixture = TestBed.createComponent(ErrorBannerComponent);
  });

  it('renders nothing when error is null', () => {
    fixture.componentRef.setInput('error', null);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.error-banner')).toBeNull();
  });

  it('renders localized message when error provided', () => {
    fixture.componentRef.setInput('error', { code: 'EMAIL_TAKEN', message: 'raw' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent.trim())
        .toBe('Acest email este deja folosit.');
  });
});
