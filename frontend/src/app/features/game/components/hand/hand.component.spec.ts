import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HandComponent } from './hand.component';
import { TEST_PIECES } from '../../../../../test-utils/game-test-data';

describe('HandComponent', () => {
  let fixture: ComponentFixture<HandComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HandComponent] });
    fixture = TestBed.createComponent(HandComponent);
  });

  it('renders one <app-piece> per piece', () => {
    fixture.componentRef.setInput('pieces', [TEST_PIECES['red5'], TEST_PIECES['red6'], TEST_PIECES['red7']]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('app-piece').length).toBe(3);
  });

  it('passes selected=true for ids in selectedIds', () => {
    fixture.componentRef.setInput('pieces', [TEST_PIECES['red5'], TEST_PIECES['red6']]);
    fixture.componentRef.setInput('selectedIds', new Set([TEST_PIECES['red5'].id]));
    fixture.detectChanges();
    const pieces = fixture.nativeElement.querySelectorAll('.piece');
    expect(pieces[0].classList.contains('selected')).toBeTrue();
    expect(pieces[1].classList.contains('selected')).toBeFalse();
  });

  it('passes mustUse=true for mustUsePieceId match', () => {
    fixture.componentRef.setInput('pieces', [TEST_PIECES['red5'], TEST_PIECES['red6']]);
    fixture.componentRef.setInput('mustUsePieceId', TEST_PIECES['red5'].id);
    fixture.detectChanges();
    const pieces = fixture.nativeElement.querySelectorAll('.piece');
    expect(pieces[0].classList.contains('must-use')).toBeTrue();
    expect(pieces[1].classList.contains('must-use')).toBeFalse();
  });

  it('emits pieceClicked when a piece is clicked', () => {
    fixture.componentRef.setInput('pieces', [TEST_PIECES['red5']]);
    fixture.detectChanges();
    let emitted: number | undefined;
    fixture.componentInstance.pieceClicked.subscribe(id => emitted = id);
    fixture.nativeElement.querySelector('.piece').click();
    expect(emitted).toBe(TEST_PIECES['red5'].id);
  });
});
