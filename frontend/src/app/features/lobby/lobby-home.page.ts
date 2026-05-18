import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { IonContent, IonCard, IonCardHeader, IonCardTitle, IonCardSubtitle, IonCardContent }
    from '@ionic/angular/standalone';
import { PushNotificationsService } from '../../core/push/push-notifications.service';

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

  ngOnInit(): void {
    void this.push.ensurePermission();
  }
}
