export type DocumentStatus = 'PROCESANDO' | 'INDEXADO' | 'ERROR';

export interface UploadReceipt {
  id: string;
  estado: DocumentStatus;
}

export interface StatusView {
  id: string;
  estado: DocumentStatus;
  error: string | null;
}

export interface DocumentDetail {
  id: string;
  titulo: string;
  autor: string;
  categoria: string;
  etiquetas: string[];
  version: string;
  nombreArchivo: string;
  mimeType: string;
  tamanoBytes: number;
  estado: DocumentStatus;
  error: string | null;
  creadoEn: string;
  actualizadoEn: string;
  contenido: string | null;
}

export interface SearchHit {
  id: string;
  titulo: string;
  tituloResaltado: string;
  fragmentos: string[];
  autor: string | null;
  categoria: string | null;
  etiquetas: string[];
  version: string | null;
  nombreArchivo: string | null;
  mimeType: string | null;
}

export interface SearchPage {
  query: string;
  page: number;
  size: number;
  total: number;
  timedOut: boolean;
  tookMs: number;
  results: SearchHit[];
}

export interface TrackedDocument {
  id: string;
  titulo: string;
  nombreArchivo: string;
  estado: DocumentStatus;
  error: string | null;
}

export interface StatusEvent {
  type: string;
  documentId: string;
  status: DocumentStatus;
  error: string | null;
  occurredAt: string;
}
