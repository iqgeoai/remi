import { ErrorMessagePipe } from './error-message.pipe';

describe('ErrorMessagePipe', () => {
  const pipe = new ErrorMessagePipe();

  it('returns empty string for null', () => {
    expect(pipe.transform(null)).toBe('');
  });

  it('localizes known code', () => {
    expect(pipe.transform({ code: 'EMAIL_TAKEN', message: 'x' }))
        .toBe('Acest email este deja folosit.');
  });

  it('falls back to message for unknown code', () => {
    expect(pipe.transform({ code: 'WEIRD_CODE_123', message: 'fallback message' }))
        .toBe('fallback message');
  });

  it('falls back to UNKNOWN if neither known code nor message', () => {
    expect(pipe.transform({ code: 'WEIRD_CODE_123', message: '' }))
        .toBe('A apărut o eroare neașteptată.');
  });
});
