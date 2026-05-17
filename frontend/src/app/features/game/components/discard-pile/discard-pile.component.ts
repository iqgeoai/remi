import { Component, ChangeDetectionStrategy, input, output, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CdkDropList, CdkDragDrop } from '@angular/cdk/drag-drop';
import { Piece } from '../../../../core/models';
import { PieceComponent } from '../piece/piece.component';

@Component({
  selector: 'app-discard-pile',
  standalone: true,
  imports: [CommonModule, CdkDropList, PieceComponent],
  templateUrl: './discard-pile.component.html',
  styleUrls: ['./discard-pile.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DiscardPileComponent {
  readonly discard = input.required<Piece[]>();      // last = top
  readonly canTake = input<boolean>(false);
  readonly takeRequested = output<number>();          // emits index taken (default top)
  readonly pieceDroppedHere = output<number>();       // emits pieceId dropped from hand (Discard action)

  readonly topPiece = computed<Piece | null>(() => {
    const d = this.discard();
    return d.length > 0 ? d[d.length - 1] : null;
  });

  readonly listId = 'discard-list';

  onTopClick(): void {
    if (!this.canTake()) return;
    this.takeRequested.emit(this.discard().length - 1);
  }

  onDrop(event: CdkDragDrop<unknown>): void {
    // Only handle if a piece was dropped from another list (the hand)
    if (event.previousContainer === event.container) return;
    const draggedPiece = event.item.data as Piece;
    if (draggedPiece && typeof draggedPiece.id === 'number') {
      this.pieceDroppedHere.emit(draggedPiece.id);
    }
  }
}
