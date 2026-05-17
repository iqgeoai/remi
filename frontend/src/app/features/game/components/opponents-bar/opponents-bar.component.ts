import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PlayerView } from '../../../../core/models';
import { OpponentSummaryComponent } from '../opponent-summary/opponent-summary.component';

interface OpponentRow {
  player: PlayerView;
  globalIdx: number;
  score: number;
}

@Component({
  selector: 'app-opponents-bar',
  standalone: true,
  imports: [CommonModule, OpponentSummaryComponent],
  templateUrl: './opponents-bar.component.html',
  styleUrls: ['./opponents-bar.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OpponentsBarComponent {
  readonly players = input.required<PlayerView[]>();
  readonly currentIdx = input.required<number>();
  readonly mySeatIdx = input.required<number>();
  readonly totals = input.required<number[]>();

  readonly opponents = computed<OpponentRow[]>(() => {
    const me = this.mySeatIdx();
    return this.players()
      .map((player, idx) => ({ player, globalIdx: idx, score: this.totals()[idx] ?? 0 }))
      .filter(row => row.globalIdx !== me);
  });
}
