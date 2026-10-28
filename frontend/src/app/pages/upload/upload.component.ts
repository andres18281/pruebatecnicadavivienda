import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { TrackedDocument } from '../../models';

@Component({
  selector: 'app-upload',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './upload.component.html'
})
export class UploadComponent {
  private readonly api = inject(ApiService);
  readonly file = signal<File | null>(null);
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);
  readonly cargas = signal<TrackedDocument[]>([]);

  readonly form = new FormGroup({
    titulo: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(300)] }),
    autor: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(200)] }),
    categoria: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    etiquetas: new FormControl('', { nonNullable: true }),
    version: new FormControl('1.0', { nonNullable: true, validators: [Validators.required, Validators.maxLength(50)] })
  });

  pick(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file.set(input.files?.[0] ?? null);
  }

  drop(event: DragEvent): void {
    event.preventDefault();
    this.file.set(event.dataTransfer?.files?.[0] ?? null);
  }

  submit(): void {
    const selected = this.file();
    if (this.form.invalid || !selected) {
      this.form.markAllAsTouched();
      this.error.set(selected ? 'Revisa los metadatos obligatorios.' : 'Selecciona un archivo TXT, Markdown o PDF.');
      return;
    }
    const value = this.form.getRawValue();
    const data = new FormData();
    data.append('file', selected);
    data.append('titulo', value.titulo);
    data.append('autor', value.autor);
    data.append('categoria', value.categoria);
    data.append('etiquetas', value.etiquetas);
    data.append('version', value.version);
    this.sending.set(true);
    this.error.set(null);
    this.api.upload(data).subscribe({
      next: (receipt) => {
        this.cargas.update((list) => [{
          id: receipt.id,
          titulo: value.titulo,
          nombreArchivo: selected.name,
          estado: receipt.estado,
          error: null
        }, ...list]);
        this.file.set(null);
        this.sending.set(false);
      },
      error: (err: { error?: { message?: string } }) => {
        this.sending.set(false);
        this.error.set(err?.error?.message ?? 'No se pudo cargar el documento.');
      }
    });
  }
}
