import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OpponentsBarComponent } from './opponents-bar.component';
import { TEST_PLAYER_ALICE, TEST_PLAYER_BOB } from '../../../../../test-utils/game-test-data';

describe('OpponentsBarComponent', () => {
  let fixture: ComponentFixture<OpponentsBarComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [OpponentsBarComponent] });
    fixture = TestBed.createComponent(OpponentsBarComponent);
  });

  it('filters out my own seat', () => {
    fixture.componentRef.setInput('players', [TEST_PLAYER_ALICE, TEST_PLAYER_BOB]);
    fixture.componentRef.setInput('currentIdx', 0);
    fixture.componentRef.setInput('mySeatIdx', 0);
    fixture.componentRef.setInput('totals', [0, 0]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('app-opponent-summary').length).toBe(1);
  });

  it('marks active opponent when currentIdx is theirs', () => {
    fixture.componentRef.setInput('players', [TEST_PLAYER_ALICE, TEST_PLAYER_BOB]);
    fixture.componentRef.setInput('currentIdx', 1);   // bob's turn
    fixture.componentRef.setInput('mySeatIdx', 0);
    fixture.componentRef.setInput('totals', [0, 0]);
    fixture.detectChanges();
    const opp = fixture.nativeElement.querySelector('app-opponent-summary .opp');
    expect(opp.classList).toContain('active');
  });

  it('passes per-player score', () => {
    fixture.componentRef.setInput('players', [TEST_PLAYER_ALICE, TEST_PLAYER_BOB]);
    fixture.componentRef.setInput('currentIdx', 0);
    fixture.componentRef.setInput('mySeatIdx', 0);
    fixture.componentRef.setInput('totals', [100, -50]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.score').textContent.trim()).toBe('-50');
  });
});
