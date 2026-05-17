import { ComponentFixture, TestBed } from '@angular/core/testing';
import { StockPileComponent } from './stock-pile.component';

describe('StockPileComponent', () => {
  let fixture: ComponentFixture<StockPileComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [StockPileComponent] });
    fixture = TestBed.createComponent(StockPileComponent);
  });

  it('renders count', () => {
    fixture.componentRef.setInput('count', 42);
    fixture.componentRef.setInput('canDraw', false);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.count').textContent.trim()).toBe('42');
  });

  it('button disabled when canDraw=false', () => {
    fixture.componentRef.setInput('count', 10);
    fixture.componentRef.setInput('canDraw', false);
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('.stock');
    expect(btn.disabled).toBeTrue();
  });

  it('emits clicked when canDraw=true and clicked', () => {
    fixture.componentRef.setInput('count', 10);
    fixture.componentRef.setInput('canDraw', true);
    fixture.detectChanges();
    let emitted = false;
    fixture.componentInstance.clicked.subscribe(() => emitted = true);
    fixture.nativeElement.querySelector('.stock').click();
    expect(emitted).toBeTrue();
  });

  it('does not emit when canDraw=false and clicked', () => {
    fixture.componentRef.setInput('count', 10);
    fixture.componentRef.setInput('canDraw', false);
    fixture.detectChanges();
    let emitted = false;
    fixture.componentInstance.clicked.subscribe(() => emitted = true);
    fixture.nativeElement.querySelector('.stock').click();
    expect(emitted).toBeFalse();
  });
});
