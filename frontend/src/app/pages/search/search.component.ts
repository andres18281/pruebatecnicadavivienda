import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { safeHighlight } from '../../core/format';
import { SearchPage } from '../../models';

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './search.component.html'
})
export class SearchComponent {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly destroyRef = inject(DestroyRef);

  readonly page = signal<SearchPage | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly form = new FormGroup({
    q: new FormControl('', { nonNullable: true }),
    categoria: new FormControl('', { nonNullable: true }),
    etiqueta: new FormControl('', { nonNullable: true })
  });

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const query = params.get('q') ?? '';
      this.form.patchValue({
        q: query,
        categoria: params.get('categoria') ?? '',
        etiqueta: params.get('etiqueta') ?? ''
      });
      if (query.trim()) {
        this.execute(query, Number(params.get('page') ?? 0), params.get('categoria') ?? '', params.get('etiqueta') ?? '');
      }
    });
  }

  submit(page = 0): void {
    const value = this.form.getRawValue();
    void this.router.navigate(['/buscar'], {
      queryParams: {
        q: value.q,
        page,
        categoria: value.categoria || null,
        etiqueta: value.etiqueta || null
      }
    });
  }

  html(value: string): SafeHtml {
    return this.sanitizer.bypassSecurityTrustHtml(safeHighlight(value));
  }

  private execute(query: string, page: number, categoria: string, etiqueta: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.search(query, Number.isFinite(page) ? page : 0, 10, categoria, etiqueta).subscribe({
      next: (result) => {
        this.page.set(result);
        this.loading.set(false);
      },
      error: (err: { error?: { message?: string } }) => {
        this.loading.set(false);
        this.page.set(null);
        this.error.set(err?.error?.message ?? 'No se pudo buscar.');
      }
    });
  }
}
