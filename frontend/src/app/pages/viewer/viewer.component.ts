import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DomSanitizer, SafeHtml, SafeResourceUrl } from '@angular/platform-browser';
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
  readonly loading = signal(true);
  readonly tab = signal<'archivo' | 'texto'>('archivo');
  readonly previewUrl = signal<SafeResourceUrl | null>(null);
  readonly originalText = signal<string | null>(null);
  readonly previewError = signal<string | null>(null);
  readonly bytes = formatBytes;

  private objectUrl: string | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => this.revoke());

    this.route.paramMap.pipe(
      switchMap((params) => {
        this.resetPreview();
        this.loading.set(true);
        this.document.set(null);
        this.error.set(null);
        return this.api.detail(params.get('id') ?? '');
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (detail) => {
        this.show(detail);
        this.loadOriginal(detail);
      },
      error: (err: { error?: { message?: string } }) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'No se encontro el documento.');
      }
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

  isPdf(doc: DocumentDetail): boolean {
    return doc.mimeType === 'application/pdf' || doc.nombreArchivo.toLowerCase().endsWith('.pdf');
  }

  selectTab(tab: 'archivo' | 'texto'): void {
    this.tab.set(tab);
  }

  private show(detail: DocumentDetail): void {
    this.document.set(detail);
    this.loading.set(false);
    this.error.set(null);
    if (detail.estado === 'PROCESANDO') {
      this.notifications.subscribe(detail.id);
    }
  }

  private loadOriginal(detail: DocumentDetail): void {
    this.api.file(detail.id).subscribe({
      next: (blob) => {
        if (!blob || blob.size === 0 || (blob.type && blob.type.includes('json'))) {
          this.previewError.set('No se pudo abrir la vista previa del archivo.');
          return;
        }
        if (this.isPdf(detail)) {
          this.revoke();
          this.objectUrl = URL.createObjectURL(blob);
          this.previewUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(this.objectUrl));
          return;
        }
        void blob.text().then((text) => this.originalText.set(text));
      },
      error: () => this.previewError.set('No se pudo abrir la vista previa del archivo.')
    });
  }

  private resetPreview(): void {
    this.tab.set('archivo');
    this.previewUrl.set(null);
    this.originalText.set(null);
    this.previewError.set(null);
    this.revoke();
  }

  private revoke(): void {
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
      this.objectUrl = null;
    }
  }
}
