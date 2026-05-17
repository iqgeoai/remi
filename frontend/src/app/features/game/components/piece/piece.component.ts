import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Piece } from '../../../../core/models';
import { getPieceColorClass } from '../../shared/pieces.utils';

@Component({
  selector: 'app-piece',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './piece.component.html',
  styleUrls: ['./piece.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PieceComponent {
  readonly piece = input.required<Piece>();
  readonly selected = input<boolean>(false);
  readonly mustUse = input<boolean>(false);
  readonly select = output<number>();

  colorClass(): string { return getPieceColorClass(this.piece()); }

  onTap(): void { this.select.emit(this.piece().id); }
}
