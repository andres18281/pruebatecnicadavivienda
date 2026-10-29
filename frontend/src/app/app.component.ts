import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NotificationService } from './core/notification.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html'
})
export class AppComponent {
  readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);

  constructor() {
    this.notifications.connect();
    for (const item of this.notifications.uploads()) {
      if (item.estado === 'PROCESANDO') {
        this.notifications.subscribe(item.id);
      }
    }
  }

  open(documentId: string): void {
    void this.router.navigate(['/documentos', documentId]);
  }
}
