import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActionBarComponent } from './action-bar.component';

describe('ActionBarComponent', () => {
  let fixture: ComponentFixture<ActionBarComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ActionBarComponent] });
    fixture = TestBed.createComponent(ActionBarComponent);
  });

  it('shows Trage button when canDraw=true', () => {
    fixture.componentRef.setInput('canDraw', true);
    fixture.detectChanges();
    const btns = fixture.nativeElement.querySelectorAll('ion-button');
    expect(Array.from(btns).some((b: any) => b.textContent.includes('Trage'))).toBeTrue();
  });

  it('shows Adaugă meld when selectedCount>0 and canEtalat=true', () => {
    fixture.componentRef.setInput('selectedCount', 3);
    fixture.componentRef.setInput('canEtalat', true);
    fixture.detectChanges();
    const btns = fixture.nativeElement.querySelectorAll('ion-button');
    expect(Array.from(btns).some((b: any) => b.textContent.includes('Adaugă meld'))).toBeTrue();
  });

  it('shows Etalează (N) when proposedMelds non-empty', () => {
    fixture.componentRef.setInput('proposedMelds', [
      { type: 'GROUP', pieceIds: [1,2,3] },
      { type: 'SUITE', pieceIds: [4,5,6] },
    ]);
    fixture.detectChanges();
    const btns = fixture.nativeElement.querySelectorAll('ion-button');
    expect(Array.from(btns).some((b: any) => b.textContent.includes('Etalează (2)'))).toBeTrue();
  });

  it('emits drawClicked', () => {
    fixture.componentRef.setInput('canDraw', true);
    fixture.detectChanges();
    let emitted = false;
    fixture.componentInstance.drawClicked.subscribe(() => emitted = true);
    const drawBtn = Array.from(fixture.nativeElement.querySelectorAll('ion-button'))
      .find((b: any) => b.textContent.includes('Trage')) as HTMLElement;
    drawBtn.click();
    expect(emitted).toBeTrue();
  });
});
