import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { WsConnectionState } from '../../core/ws/ws-state';

@Component({
  selector: 'app-ws-indicator',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ws-indicator.component.html',
  styleUrls: ['./ws-indicator.component.scss'],
})
export class WsIndicatorComponent {
  @Input() state: WsConnectionState = 'DISCONNECTED';
}
