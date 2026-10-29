import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { formatBytes } from '../../core/format';
import { NotificationService } from '../../core/notification.service';

@Component({
  selector: 'app-upload',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './upload.component.html'
})
export class UploadComponent {
  static readonly maxUploadBytes = 10 * 1024 * 1024;

  private readonly api = inject(ApiService);
  readonly notifications = inject(NotificationService);
  readonly file = signal<File | null>(null);
  readonly over = signal(false);
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);
  readonly size = formatBytes;

  readonly form = new FormGroup({
    titulo: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(300)] }),
    autor: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(200)] }),
    categoria: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    etiquetas: new FormControl('', { nonNullable: true }),
    version: new FormControl('1.0', { nonNullable: true, validators: [Validators.required, Validators.maxLength(50)] })
  });

  pick(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.accept(input.files?.[0] ?? null);
    input.value = '';
  }

  onOver(event: DragEvent, value: boolean): void {
    event.preventDefault();
    this.over.set(value);
  }

  drop(event: DragEvent): void {
    event.preventDefault();
    this.over.set(false);
    this.accept(event.dataTransfer?.files?.[0] ?? null);
  }

  submit(): void {
    const selected = this.file();
    if (this.form.invalid || !selected) {
      this.form.markAllAsTouched();
      this.error.set(selected ? 'Revisa los metadatos obligatorios.' : 'Selecciona un archivo TXT, Markdown o PDF.');
      return;
    }
    if (selected.size > UploadComponent.maxUploadBytes) {
      this.error.set('El archivo supera el maximo de 10 MB.');
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
        this.notifications.track({
          id: receipt.id,
          titulo: value.titulo,
          nombreArchivo: selected.name,
          estado: receipt.estado,
          error: null,
          percent: 8,
          stage: 'RECEIVED',
          label: 'Documento recibido, esperando al worker'
        });
        this.file.set(null);
        this.sending.set(false);
      },
      error: (err: { error?: { message?: string } }) => {
        this.sending.set(false);
        this.error.set(err?.error?.message ?? 'No se pudo cargar el documento.');
      }
    });
  }

  private accept(selected: File | null): void {
    if (!selected) {
      this.file.set(null);
      return;
    }
    if (selected.size > UploadComponent.maxUploadBytes) {
      this.file.set(null);
      this.error.set('El archivo supera el maximo de 10 MB.');
      return;
    }
    this.error.set(null);
    this.file.set(selected);
  }
}
