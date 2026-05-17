import { Component, ChangeDetectionStrategy, input, output, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonContent, IonButton } from '@ionic/angular/standalone';

export interface RoundResult {
  playerIdx: number;
  name: string;
  base: number;
  melded: number;
  handCount: number;
}

@Component({
  selector: 'app-round-end-modal',
  standalone: true,
  imports: [CommonModule, IonContent, IonButton],
  templateUrl: './round-end-modal.component.html',
  styleUrls: ['./round-end-modal.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RoundEndModalComponent {
  readonly results = input.required<RoundResult[]>();
  readonly winnerName = input.required<string>();
  readonly closeRequested = output<void>();

  readonly winnerIdx = computed(() => {
    const r = this.results();
    let best = 0;
    for (let i = 1; i < r.length; i++) if (r[i].base > r[best].base) best = i;
    return best;
  });

  format(n: number): string { return n > 0 ? `+${n}` : `${n}`; }
}
