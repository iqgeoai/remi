import { animate, state, style, transition, trigger } from '@angular/animations';

export const pieceSelectedAnim = trigger('pieceSelected', [
  state('selected',   style({ transform: 'translateY(-8px)' })),
  state('unselected', style({ transform: 'translateY(0)' })),
  transition('* <=> *', animate('150ms ease-out')),
]);

export const drawSlideAnim = trigger('drawSlide', [
  transition(':enter', [
    style({ transform: 'translate(-100px, -50px) rotate(-15deg)', opacity: 0 }),
    animate('300ms ease-out', style({ transform: 'translate(0,0) rotate(0)', opacity: 1 })),
  ]),
]);

export const discardSlideAnim = trigger('discardSlide', [
  transition(':enter', [
    style({ transform: 'translateY(-100px)', opacity: 0 }),
    animate('250ms ease-in', style({ transform: 'translateY(0)', opacity: 1 })),
  ]),
]);

export const opponentActiveAnim = trigger('opponentActive', [
  state('active',   style({ boxShadow: '0 0 0 2px rgba(212,164,67,0.4)' })),
  state('inactive', style({ boxShadow: '0 0 0 0 transparent' })),
  transition('* <=> *', animate('200ms ease-out')),
]);
