import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MeldCardComponent } from './meld-card.component';
import { TEST_PIECES } from '../../../../../test-utils/game-test-data';
import { Meld } from '../../../../core/models';

describe('MeldCardComponent', () => {
  let fixture: ComponentFixture<MeldCardComponent>;

  const groupMeld: Meld = {
    owner: 0, type: 'GROUP',
    pieces: [TEST_PIECES['yel10'], TEST_PIECES['blu10'], TEST_PIECES['blk10']],
    placedBy: { 9: 0, 10: 0, 11: 0 },
  };

  const suiteMeld: Meld = {
    owner: 0, type: 'SUITE',
    pieces: [TEST_PIECES['red5'], TEST_PIECES['red6'], TEST_PIECES['red7']],
    placedBy: { 2: 0, 3: 0, 4: 0 },
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [MeldCardComponent] });
    fixture = TestBed.createComponent(MeldCardComponent);
  });

  it('renders GROUP label', () => {
    fixture.componentRef.setInput('meld', groupMeld);
    fixture.componentRef.setInput('meldIdx', 0);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.type-label').textContent.trim()).toBe('Grup');
  });

  it('renders SUITE label', () => {
    fixture.componentRef.setInput('meld', suiteMeld);
    fixture.componentRef.setInput('meldIdx', 1);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.type-label').textContent.trim()).toBe('Suită');
  });

  it('renders all pieces', () => {
    fixture.componentRef.setInput('meld', groupMeld);
    fixture.componentRef.setInput('meldIdx', 0);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('app-piece').length).toBe(3);
  });
});
