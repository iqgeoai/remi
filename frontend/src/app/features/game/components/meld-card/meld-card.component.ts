import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CdkDropList, CdkDragDrop } from '@angular/cdk/drag-drop';
import { Meld, Piece } from '../../../../core/models';
import { PieceComponent } from '../piece/piece.component';

@Component({
  selector: 'app-meld-card',
  standalone: true,
  imports: [CommonModule, CdkDropList, PieceComponent],
  templateUrl: './meld-card.component.html',
  styleUrls: ['./meld-card.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MeldCardComponent {
  readonly meld = input.required<Meld>();
  readonly meldIdx = input.required<number>();
  readonly layoffDropped = output<{pieceId: number; meldIdx: number}>();

  get listId(): string { return 'meld-list-' + this.meldIdx(); }

  onDrop(event: CdkDragDrop<unknown>): void {
    if (event.previousContainer === event.container) return;
    const draggedPiece = event.item.data as Piece;
    if (draggedPiece && typeof draggedPiece.id === 'number') {
      this.layoffDropped.emit({ pieceId: draggedPiece.id, meldIdx: this.meldIdx() });
    }
  }
}
