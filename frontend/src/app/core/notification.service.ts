import { Injectable, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { ProgressEvent, StatusEvent, TrackedDocument } from '../models';
import { ApiService } from './api.service';

export interface Toast {
  id: number;
  documentId: string;
  titulo: string;
  estado: string;
  error: string | null;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly api = inject(ApiService);
  private socket: WebSocket | null = null;
  private attempt = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private toastSeq = 0;
  private readonly subscriptions = new Set<string>();
  private readonly eventsSubject = new Subject<StatusEvent>();

  readonly connected = signal(false);
  readonly uploads = signal<TrackedDocument[]>(this.read());
  readonly toasts = signal<Toast[]>([]);
  readonly events = this.eventsSubject.asObservable();

  connect(): void {
    if (this.socket && (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)) {
      return;
    }
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    this.socket = new WebSocket(`${protocol}://${location.host}/ws`);
    this.socket.onopen = () => {
      this.attempt = 0;
      this.connected.set(true);
      for (const id of this.subscriptions) {
        this.sendSubscribe(id);
      }
      this.resync();
    };
    this.socket.onmessage = (event: MessageEvent<string>) => this.onMessage(String(event.data));
    this.socket.onclose = () => {
      this.connected.set(false);
      this.scheduleReconnect();
    };
    this.socket.onerror = () => this.socket?.close();
  }

  track(item: TrackedDocument): void {
    const current: TrackedDocument = {
      ...item,
      percent: item.percent ?? 8,
      stage: item.stage ?? 'RECEIVED',
      label: item.label ?? 'Documento recibido, esperando al worker'
    };
    this.uploads.update((list) => [current, ...list.filter((entry) => entry.id !== current.id)].slice(0, 30));
    this.persist();
    this.subscribe(current.id);
    this.resyncOne(current.id);
  }

  subscribe(id: string): void {
    this.subscriptions.add(id);
    this.sendSubscribe(id);
  }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((toast) => toast.id !== id));
  }

  private onMessage(raw: string): void {
    let payload: StatusEvent | ProgressEvent;
    try {
      payload = JSON.parse(raw) as StatusEvent | ProgressEvent;
    } catch {
      return;
    }
    if (payload.type === 'document.progress') {
      this.onProgress(payload as ProgressEvent);
      return;
    }
    if (payload.type !== 'document.status.changed' || !payload.documentId) {
      return;
    }
    const event = payload as StatusEvent;
    const estado = event.status;
    this.uploads.update((list) => list.map((item) => item.id === event.documentId
      ? {
          ...item,
          estado,
          error: event.error,
          percent: estado === 'INDEXADO' ? 100 : item.percent,
          stage: estado === 'INDEXADO' ? 'INDEXED' : estado === 'ERROR' ? 'FAILED' : item.stage,
          label: estado === 'INDEXADO' ? 'Indexado en Elasticsearch' : estado === 'ERROR' ? (event.error ?? 'Error al procesar') : item.label
        }
      : item));
    this.persist();
    this.eventsSubject.next(event);
    const known = this.uploads().find((item) => item.id === event.documentId);
    if (estado === 'INDEXADO' || estado === 'ERROR') {
      const toast: Toast = {
        id: ++this.toastSeq,
        documentId: event.documentId,
        titulo: known?.titulo ?? 'Documento',
        estado,
        error: event.error
      };
      this.toasts.update((list) => [toast, ...list].slice(0, 4));
    }
  }

  private onProgress(event: ProgressEvent): void {
    if (!event.documentId) {
      return;
    }
    this.uploads.update((list) => list.map((item) => {
      if (item.id !== event.documentId || item.estado === 'INDEXADO' || item.estado === 'ERROR') {
        return item;
      }
      return {
        ...item,
        percent: Math.max(item.percent ?? 0, event.percent),
        stage: event.stage,
        label: event.label
      };
    }));
    this.persist();
  }

  private resync(): void {
    for (const item of this.uploads()) {
      if (item.estado === 'PROCESANDO') {
        this.resyncOne(item.id);
      }
    }
  }

  private resyncOne(id: string): void {
    this.api.status(id).subscribe({
      next: (status) => {
        this.uploads.update((list) => list.map((item) => item.id === id
          ? {
              ...item,
              estado: status.estado,
              error: status.error,
              percent: status.estado === 'INDEXADO' ? 100 : item.percent,
              stage: status.estado === 'INDEXADO' ? 'INDEXED' : status.estado === 'ERROR' ? 'FAILED' : item.stage,
              label: status.estado === 'INDEXADO'
                ? 'Indexado en Elasticsearch'
                : status.estado === 'ERROR'
                  ? (status.error ?? item.label)
                  : item.label
            }
          : item));
        this.persist();
      },
      error: () => undefined
    });
  }

  private sendSubscribe(id: string): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      return;
    }
    this.socket.send(JSON.stringify({ action: 'subscribe', documentId: id }));
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
    }
    const delay = Math.min(30_000, 1000 * 2 ** this.attempt);
    this.attempt += 1;
    this.reconnectTimer = setTimeout(() => this.connect(), delay);
  }

  private persist(): void {
    try {
      localStorage.setItem('techdocs.uploads', JSON.stringify(this.uploads()));
    } catch {
      // El navegador puede bloquear el almacenamiento.
    }
  }

  private read(): TrackedDocument[] {
    try {
      const raw = localStorage.getItem('techdocs.uploads');
      const parsed = raw ? JSON.parse(raw) as TrackedDocument[] : [];
      return parsed.map((item) => ({
        ...item,
        percent: item.percent ?? (item.estado === 'INDEXADO' ? 100 : item.estado === 'PROCESANDO' ? 8 : 0),
        stage: item.stage ?? null,
        label: item.label ?? null
      }));
    } catch {
      return [];
    }
  }
}
