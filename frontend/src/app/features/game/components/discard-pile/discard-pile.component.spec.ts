import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DiscardPileComponent } from './discard-pile.component';
import { TEST_PIECES } from '../../../../../test-utils/game-test-data';

describe('DiscardPileComponent', () => {
  let fixture: ComponentFixture<DiscardPileComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [DiscardPileComponent] });
    fixture = TestBed.createComponent(DiscardPileComponent);
  });

  it('renders empty state when discard is empty', () => {
    fixture.componentRef.setInput('discard', []);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.empty')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-piece')).toBeNull();
  });

  it('renders top piece when discard non-empty', () => {
    fixture.componentRef.setInput('discard', [TEST_PIECES['red1'], TEST_PIECES['red7']]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-piece')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.num')?.textContent.trim()).toBe('7');
  });

  it('shows +N when discard has more than 1', () => {
    fixture.componentRef.setInput('discard', [TEST_PIECES['red1'], TEST_PIECES['red7'], TEST_PIECES['blue7']]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.count')?.textContent.trim()).toBe('+2');
  });

  it('emits takeRequested with top idx when canTake and clicked', () => {
    fixture.componentRef.setInput('discard', [TEST_PIECES['red1'], TEST_PIECES['red7']]);
    fixture.componentRef.setInput('canTake', true);
    fixture.detectChanges();
    let emitted: number | undefined;
    fixture.componentInstance.takeRequested.subscribe(idx => emitted = idx);
    fixture.nativeElement.querySelector('.top-clickable').click();
    expect(emitted).toBe(1);   // top idx
  });

  it('does not emit when !canTake', () => {
    fixture.componentRef.setInput('discard', [TEST_PIECES['red7']]);
    fixture.componentRef.setInput('canTake', false);
    fixture.detectChanges();
    let emitted = false;
    fixture.componentInstance.takeRequested.subscribe(() => emitted = true);
    fixture.nativeElement.querySelector('.top-clickable').click();
    expect(emitted).toBeFalse();
  });
});
