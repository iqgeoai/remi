import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TurnTimerComponent } from './turn-timer.component';

describe('TurnTimerComponent', () => {
  let fixture: ComponentFixture<TurnTimerComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [TurnTimerComponent] });
    fixture = TestBed.createComponent(TurnTimerComponent);
  });

  it('renders num seconds', () => {
    fixture.componentRef.setInput('secondsLeft', 90);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.num').textContent.trim()).toBe('90s');
  });

  it('applies warn class when seconds <= 30 and > 10', () => {
    fixture.componentRef.setInput('secondsLeft', 25);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.timer').classList).toContain('warn');
    expect(fixture.nativeElement.querySelector('.timer').classList).not.toContain('critical');
  });

  it('applies critical class when seconds <= 10', () => {
    fixture.componentRef.setInput('secondsLeft', 5);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.timer').classList).toContain('critical');
  });

  it('stroke-dashoffset shrinks as secondsLeft decreases', () => {
    fixture.componentRef.setInput('secondsLeft', 60);
    fixture.componentRef.setInput('totalSeconds', 120);
    fixture.detectChanges();
    const circle = fixture.nativeElement.querySelectorAll('circle')[1];
    const expectedOffset = (2 * Math.PI * 18) * (1 - 60/120);
    // Allow small float tolerance
    expect(Math.abs(parseFloat(circle.getAttribute('stroke-dashoffset')) - expectedOffset)).toBeLessThan(0.01);
  });
});
