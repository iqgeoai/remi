import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Piece } from '../../../../core/models';
import { PieceComponent } from '../piece/piece.component';

@Component({
  selector: 'app-atu-display',
  standalone: true,
  imports: [CommonModule, PieceComponent],
  templateUrl: './atu-display.component.html',
  styleUrls: ['./atu-display.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AtuDisplayComponent {
  readonly atu = input.required<Piece>();

  readonly doubleGame = computed(() => {
    const a = this.atu();
    return a.isJoker || a.num === 1;
  });
}
