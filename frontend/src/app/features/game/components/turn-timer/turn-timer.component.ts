import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-turn-timer',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './turn-timer.component.html',
  styleUrls: ['./turn-timer.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TurnTimerComponent {
  readonly secondsLeft = input.required<number>();
  readonly totalSeconds = input<number>(120);

  readonly circumference = 2 * Math.PI * 18;   // r=18
  readonly dashOffset = computed(() => {
    const ratio = Math.max(0, Math.min(1, this.secondsLeft() / this.totalSeconds()));
    return this.circumference * (1 - ratio);
  });
  readonly critical = computed(() => this.secondsLeft() <= 10);
  readonly warn = computed(() => this.secondsLeft() <= 30 && this.secondsLeft() > 10);
}
