import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { SearchPage, UploadReceipt } from '../models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  upload(form: FormData): Observable<UploadReceipt> {
    return this.http.post<UploadReceipt>('/api/v1/documents', form);
  }

  search(query: string, page: number, size: number, categoria: string, etiqueta: string): Observable<SearchPage> {
    let params = new HttpParams().set('q', query).set('page', page).set('size', size);
    if (categoria.trim()) {
      params = params.set('categoria', categoria.trim());
    }
    if (etiqueta.trim()) {
      params = params.set('etiqueta', etiqueta.trim());
    }
    return this.http.get<SearchPage>('/api/v1/search', { params });
  }
}
