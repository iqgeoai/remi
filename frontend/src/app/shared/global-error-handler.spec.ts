import { TestBed } from '@angular/core/testing';
import { ToastController } from '@ionic/angular/standalone';
import { GlobalErrorHandler } from './global-error-handler';

describe('GlobalErrorHandler', () => {
  let handler: GlobalErrorHandler;
  let toastSpy: jasmine.SpyObj<ToastController>;

  beforeEach(() => {
    toastSpy = jasmine.createSpyObj('ToastController', ['create']);
    toastSpy.create.and.returnValue(Promise.resolve({ present: () => Promise.resolve() } as any));
    TestBed.configureTestingModule({
      providers: [
        GlobalErrorHandler,
        { provide: ToastController, useValue: toastSpy },
      ],
    });
    handler = TestBed.inject(GlobalErrorHandler);
  });

  it('logs to console and shows toast on unhandled error', async () => {
    spyOn(console, 'error');
    handler.handleError(new Error('boom'));
    expect(console.error).toHaveBeenCalled();
    expect(toastSpy.create).toHaveBeenCalledWith(jasmine.objectContaining({
      message: 'A apărut o eroare neașteptată.',
      duration: 4000,
      color: 'danger',
    }));
  });
});
