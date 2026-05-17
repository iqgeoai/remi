import { Component, ChangeDetectionStrategy, input, output, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonButton } from '@ionic/angular/standalone';
import { MeldProposal } from '../../../../core/models';

@Component({
  selector: 'app-action-bar',
  standalone: true,
  imports: [CommonModule, IonButton],
  templateUrl: './action-bar.component.html',
  styleUrls: ['./action-bar.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActionBarComponent {
  readonly canDraw = input<boolean>(false);
  readonly selectedCount = input<number>(0);
  readonly canEtalat = input<boolean>(false);
  readonly proposedMelds = input<MeldProposal[]>([]);
  readonly mustUseHint = input<boolean>(false);

  readonly drawClicked = output<void>();
  readonly addMeldClicked = output<void>();
  readonly etalatClicked = output<void>();
  readonly cancelClicked = output<void>();

  readonly hasProposed = computed(() => this.proposedMelds().length > 0);
  readonly hasSelection = computed(() => this.selectedCount() > 0);
  readonly proposedLabel = computed(() => `Etalează (${this.proposedMelds().length})`);
}
