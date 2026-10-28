export type DocumentStatus = 'PROCESANDO' | 'INDEXADO' | 'ERROR';

export interface UploadReceipt {
  id: string;
  estado: DocumentStatus;
}

export interface TrackedDocument {
  id: string;
  titulo: string;
  nombreArchivo: string;
  estado: DocumentStatus;
  error: string | null;
}
