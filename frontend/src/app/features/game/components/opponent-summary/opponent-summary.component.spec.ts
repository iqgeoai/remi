import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OpponentSummaryComponent } from './opponent-summary.component';
import { TEST_PLAYER_BOB } from '../../../../../test-utils/game-test-data';

describe('OpponentSummaryComponent', () => {
  let fixture: ComponentFixture<OpponentSummaryComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [OpponentSummaryComponent] });
    fixture = TestBed.createComponent(OpponentSummaryComponent);
  });

  it('renders name, handCount, score', () => {
    fixture.componentRef.setInput('player', TEST_PLAYER_BOB);
    fixture.componentRef.setInput('score', 50);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.name').textContent.trim()).toBe('Bob');
    expect(fixture.nativeElement.querySelector('.pieces').textContent.trim()).toBe('14 pcs');
    expect(fixture.nativeElement.querySelector('.score').textContent.trim()).toBe('+50');
  });

  it('formats negative score without extra plus', () => {
    fixture.componentRef.setInput('player', TEST_PLAYER_BOB);
    fixture.componentRef.setInput('score', -30);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.score').textContent.trim()).toBe('-30');
  });

  it('applies active class when active=true', () => {
    fixture.componentRef.setInput('player', TEST_PLAYER_BOB);
    fixture.componentRef.setInput('active', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.opp').classList).toContain('active');
  });

  it('applies etalat class when player.hasEtalat=true', () => {
    fixture.componentRef.setInput('player', { ...TEST_PLAYER_BOB, hasEtalat: true });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.opp').classList).toContain('etalat');
  });
});
