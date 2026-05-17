import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ErrorMessagePipe } from '../../core/i18n/error-message.pipe';
import { ApiError } from '../../core/models';

@Component({
  selector: 'app-error-banner',
  standalone: true,
  imports: [CommonModule, ErrorMessagePipe],
  templateUrl: './error-banner.component.html',
  styleUrls: ['./error-banner.component.scss'],
})
export class ErrorBannerComponent {
  @Input() error: ApiError | null = null;
}
