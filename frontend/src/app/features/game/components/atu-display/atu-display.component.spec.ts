import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AtuDisplayComponent } from './atu-display.component';
import { TEST_PIECES } from '../../../../../test-utils/game-test-data';

describe('AtuDisplayComponent', () => {
  let fixture: ComponentFixture<AtuDisplayComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [AtuDisplayComponent] });
    fixture = TestBed.createComponent(AtuDisplayComponent);
  });

  it('shows piece + Atu label', () => {
    fixture.componentRef.setInput('atu', TEST_PIECES['red5']);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.label')?.textContent.trim()).toBe('Atu');
    expect(fixture.nativeElement.querySelector('app-piece')).toBeTruthy();
  });

  it('shows JOC DUBLU when atu is joker', () => {
    fixture.componentRef.setInput('atu', TEST_PIECES['joker1']);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.double-badge')?.textContent.trim()).toBe('JOC DUBLU');
  });

  it('shows JOC DUBLU when atu is 1', () => {
    fixture.componentRef.setInput('atu', TEST_PIECES['red1']);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.double-badge')).toBeTruthy();
  });

  it('hides badge for non-double atu', () => {
    fixture.componentRef.setInput('atu', TEST_PIECES['red5']);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.double-badge')).toBeNull();
  });
});
