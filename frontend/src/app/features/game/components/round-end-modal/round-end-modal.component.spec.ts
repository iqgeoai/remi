import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RoundEndModalComponent, RoundResult } from './round-end-modal.component';

describe('RoundEndModalComponent', () => {
  let fixture: ComponentFixture<RoundEndModalComponent>;

  const results: RoundResult[] = [
    { playerIdx: 0, name: 'Alice', base: 60,  melded: 2, handCount: 0 },
    { playerIdx: 1, name: 'Bob',   base: -20, melded: 1, handCount: 5 },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [RoundEndModalComponent] });
    fixture = TestBed.createComponent(RoundEndModalComponent);
  });

  it('renders all results', () => {
    fixture.componentRef.setInput('results', results);
    fixture.componentRef.setInput('winnerName', 'Alice');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(2);
  });

  it('highlights winner row', () => {
    fixture.componentRef.setInput('results', results);
    fixture.componentRef.setInput('winnerName', 'Alice');
    fixture.detectChanges();
    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows[0].classList.contains('win')).toBeTrue();   // Alice has higher base
    expect(rows[1].classList.contains('win')).toBeFalse();
  });

  it('formats negative score without extra plus', () => {
    fixture.componentRef.setInput('results', results);
    fixture.componentRef.setInput('winnerName', 'Alice');
    fixture.detectChanges();
    const cells = fixture.nativeElement.querySelectorAll('tbody td.num');
    // First row's "Rundă" cell
    expect(cells[0].textContent.trim()).toBe('+60');
    expect(cells[2].textContent.trim()).toBe('-20');
  });

  it('emits closeRequested on button click', () => {
    fixture.componentRef.setInput('results', results);
    fixture.componentRef.setInput('winnerName', 'Alice');
    fixture.detectChanges();
    let emitted = false;
    fixture.componentInstance.closeRequested.subscribe(() => emitted = true);
    fixture.nativeElement.querySelector('ion-button').click();
    expect(emitted).toBeTrue();
  });
});
