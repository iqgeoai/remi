import { Pipe, PipeTransform } from '@angular/core';
import { ApiError } from '../models';
import { localizeError } from './errors';

@Pipe({ name: 'errorMessage', standalone: true })
export class ErrorMessagePipe implements PipeTransform {
  transform(error: ApiError | null | undefined): string {
    return localizeError(error);
  }
}
