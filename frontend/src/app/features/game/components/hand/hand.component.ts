import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CdkDragDrop, CdkDrag, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { Piece } from '../../../../core/models';
import { PieceComponent } from '../piece/piece.component';

@Component({
  selector: 'app-hand',
  standalone: true,
  imports: [CommonModule, CdkDrag, CdkDropList, PieceComponent],
  templateUrl: './hand.component.html',
  styleUrls: ['./hand.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HandComponent {
  readonly pieces = input.required<Piece[]>();
  readonly selectedIds = input<Set<number>>(new Set<number>());
  readonly mustUsePieceId = input<number | null>(null);

  readonly pieceClicked = output<number>();
  readonly reorder = output<{from: number; to: number}>();

  // For CdkDropList to know which lists can drop here (the hand) — set externally by parent
  readonly connectedTo = input<string[]>([]);

  // List ID used by other lists (table-zone, meld-card) to drop pieces into the hand if ever needed (not used in 4b)
  readonly listId = 'hand-list';

  isSelected(piece: Piece): boolean { return this.selectedIds().has(piece.id); }
  isMustUse(piece: Piece): boolean { return this.mustUsePieceId() === piece.id; }

  trackById(_index: number, piece: Piece): number { return piece.id; }

  onDrop(event: CdkDragDrop<Piece[]>): void {
    if (event.previousContainer === event.container) {
      // Reorder within the hand
      this.reorder.emit({ from: event.previousIndex, to: event.currentIndex });
    }
    // Drops INTO other lists (discard, meld) are handled by THOSE lists' (cdkDropListDropped) events
  }
}
