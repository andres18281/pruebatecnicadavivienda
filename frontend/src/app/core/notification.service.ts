import { Injectable, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { StatusEvent, TrackedDocument } from '../models';
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
    this.uploads.update((list) => [item, ...list.filter((current) => current.id !== item.id)].slice(0, 30));
    this.persist();
    this.subscribe(item.id);
    this.resyncOne(item.id);
  }

  subscribe(id: string): void {
    this.subscriptions.add(id);
    this.sendSubscribe(id);
  }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((toast) => toast.id !== id));
  }

  private onMessage(raw: string): void {
    let event: StatusEvent;
    try {
      event = JSON.parse(raw) as StatusEvent;
    } catch {
      return;
    }
    if (event.type !== 'document.status.changed' || !event.documentId) {
      return;
    }
    const estado = event.status;
    this.uploads.update((list) => list.map((item) => item.id === event.documentId
      ? { ...item, estado, error: event.error }
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
          ? { ...item, estado: status.estado, error: status.error }
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
      return raw ? JSON.parse(raw) as TrackedDocument[] : [];
    } catch {
      return [];
    }
  }
}
