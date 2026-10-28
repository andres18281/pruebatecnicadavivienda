import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import DOMPurify from 'dompurify';
import { marked } from 'marked';
import { switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { formatBytes } from '../../core/format';
import { NotificationService } from '../../core/notification.service';
import { DocumentDetail } from '../../models';

@Component({
  selector: 'app-viewer',
  standalone: true,
  imports: [RouterLink, DatePipe],
  templateUrl: './viewer.component.html'
})
export class ViewerComponent {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly notifications = inject(NotificationService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly destroyRef = inject(DestroyRef);

  readonly document = signal<DocumentDetail | null>(null);
  readonly error = signal<string | null>(null);
  readonly bytes = formatBytes;

  constructor() {
    this.route.paramMap.pipe(
      switchMap((params) => this.api.detail(params.get('id') ?? '')),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (detail) => this.show(detail),
      error: (err: { error?: { message?: string } }) => this.error.set(err?.error?.message ?? 'No se encontro el documento.')
    });

    this.notifications.events.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((event) => {
      const current = this.document();
      if (current && event.documentId === current.id && (event.status === 'INDEXADO' || event.status === 'ERROR')) {
        this.api.detail(current.id).subscribe((detail) => this.show(detail));
      }
    });
  }

  markdown(content: string): SafeHtml {
    const raw = marked.parse(content, { async: false }) as string;
    return this.sanitizer.bypassSecurityTrustHtml(DOMPurify.sanitize(raw));
  }

  pages(content: string): { title: string; body: string }[] {
    const matches = [...content.matchAll(/--- página (\d+) ---\n?([\s\S]*?)(?=--- página \d+ ---|$)/g)];
    if (matches.length === 0) {
      return [{ title: 'Contenido', body: content }];
    }
    return matches.map((match) => ({ title: `Pagina ${match[1]}`, body: (match[2] ?? '').trim() }));
  }

  isMarkdown(doc: DocumentDetail): boolean {
    return doc.mimeType === 'text/markdown' || doc.nombreArchivo.toLowerCase().endsWith('.md') || doc.nombreArchivo.toLowerCase().endsWith('.markdown');
  }

  private show(detail: DocumentDetail): void {
    this.document.set(detail);
    this.error.set(null);
    if (detail.estado === 'PROCESANDO') {
      this.notifications.subscribe(detail.id);
    }
  }
}
