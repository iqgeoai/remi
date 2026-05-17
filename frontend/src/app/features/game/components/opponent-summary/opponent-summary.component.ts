import { Component, ChangeDetectionStrategy, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PlayerView } from '../../../../core/models';

@Component({
  selector: 'app-opponent-summary',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './opponent-summary.component.html',
  styleUrls: ['./opponent-summary.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OpponentSummaryComponent {
  readonly player = input.required<PlayerView>();
  readonly active = input<boolean>(false);
  readonly score = input<number>(0);

  scoreLabel(): string {
    const s = this.score();
    return s > 0 ? `+${s}` : `${s}`;
  }
}
