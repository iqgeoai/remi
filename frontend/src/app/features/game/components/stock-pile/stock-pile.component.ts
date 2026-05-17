import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-stock-pile',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './stock-pile.component.html',
  styleUrls: ['./stock-pile.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StockPileComponent {
  readonly count = input.required<number>();
  readonly canDraw = input<boolean>(false);
  readonly clicked = output<void>();

  onClick(): void {
    if (this.canDraw()) this.clicked.emit();
  }
}
