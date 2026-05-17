import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Piece, Meld } from '../../../../core/models';
import { AtuDisplayComponent } from '../atu-display/atu-display.component';
import { StockPileComponent } from '../stock-pile/stock-pile.component';
import { DiscardPileComponent } from '../discard-pile/discard-pile.component';
import { MeldsAreaComponent } from '../melds-area/melds-area.component';

@Component({
  selector: 'app-table-zone',
  standalone: true,
  imports: [CommonModule, AtuDisplayComponent, StockPileComponent, DiscardPileComponent, MeldsAreaComponent],
  templateUrl: './table-zone.component.html',
  styleUrls: ['./table-zone.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TableZoneComponent {
  readonly atu = input.required<Piece>();
  readonly stockCount = input.required<number>();
  readonly discard = input.required<Piece[]>();
  readonly melds = input.required<Meld[]>();
  readonly canDraw = input<boolean>(false);
  readonly canTake = input<boolean>(false);

  readonly drawClicked = output<void>();
  readonly takeRequested = output<number>();
  readonly pieceDroppedToDiscard = output<number>();
  readonly layoffDropped = output<{pieceId: number; meldIdx: number}>();
}
