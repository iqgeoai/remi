import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Meld } from '../../../../core/models';
import { MeldCardComponent } from '../meld-card/meld-card.component';

@Component({
  selector: 'app-melds-area',
  standalone: true,
  imports: [CommonModule, MeldCardComponent],
  templateUrl: './melds-area.component.html',
  styleUrls: ['./melds-area.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MeldsAreaComponent {
  readonly melds = input.required<Meld[]>();
  readonly layoffDropped = output<{pieceId: number; meldIdx: number}>();
}
