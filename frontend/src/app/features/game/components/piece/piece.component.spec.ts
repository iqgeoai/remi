import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PieceComponent } from './piece.component';
import { TEST_PIECES } from '../../../../../test-utils/game-test-data';

describe('PieceComponent', () => {
  let fixture: ComponentFixture<PieceComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [PieceComponent] });
    fixture = TestBed.createComponent(PieceComponent);
  });

  it('renders num for non-joker', () => {
    fixture.componentRef.setInput('piece', TEST_PIECES['red7']);
    fixture.detectChanges();
    const numEl = fixture.nativeElement.querySelector('.num');
    expect(numEl?.textContent.trim()).toBe('7');
  });

  it('renders star for joker', () => {
    fixture.componentRef.setInput('piece', TEST_PIECES['joker1']);
    fixture.detectChanges();
    const starEl = fixture.nativeElement.querySelector('.joker-star');
    expect(starEl?.textContent.trim()).toBe('★');
  });

  it('applies color class', () => {
    fixture.componentRef.setInput('piece', TEST_PIECES['blue7']);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.piece').classList).toContain('color-blue');
  });

  it('applies selected class when selected=true', () => {
    fixture.componentRef.setInput('piece', TEST_PIECES['red5']);
    fixture.componentRef.setInput('selected', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.piece').classList).toContain('selected');
  });

  it('applies must-use class', () => {
    fixture.componentRef.setInput('piece', TEST_PIECES['red5']);
    fixture.componentRef.setInput('mustUse', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.piece').classList).toContain('must-use');
  });

  it('emits select with piece id on click', () => {
    fixture.componentRef.setInput('piece', TEST_PIECES['red5']);
    fixture.detectChanges();
    let emitted: number | undefined;
    fixture.componentInstance.select.subscribe(id => emitted = id);
    fixture.nativeElement.querySelector('.piece').click();
    expect(emitted).toBe(TEST_PIECES['red5'].id);
  });
});
