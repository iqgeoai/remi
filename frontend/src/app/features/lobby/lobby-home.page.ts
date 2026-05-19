import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { take } from 'rxjs/operators';
import { IonContent, IonCard, IonCardHeader, IonCardTitle, IonCardSubtitle, IonCardContent }
    from '@ionic/angular/standalone';
import { PushNotificationsService } from '../../core/push/push-notifications.service';
import { selectUser } from '../../store/auth/auth.selectors';

@Component({
  selector: 'app-lobby-home',
  standalone: true,
  imports: [CommonModule, RouterLink,
    IonContent, IonCard, IonCardHeader, IonCardTitle, IonCardSubtitle, IonCardContent,
  ],
  templateUrl: './lobby-home.page.html',
  styleUrls: ['./lobby-home.page.scss'],
})
export default class LobbyHomePage implements OnInit {
  private readonly push = inject(PushNotificationsService);
  private readonly store = inject(Store);
  private readonly router = inject(Router);

  ngOnInit(): void {
    void this.push.ensurePermission();
  }

  openMyStats(): void {
    this.store
      .select(selectUser)
      .pipe(take(1))
      .subscribe(user => {
        if (user) {
          void this.router.navigateByUrl(`/profile/${user.id}`);
        }
      });
  }
}
