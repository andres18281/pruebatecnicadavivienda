import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { UploadReceipt } from '../models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  upload(form: FormData): Observable<UploadReceipt> {
    return this.http.post<UploadReceipt>('/api/v1/documents', form);
  }
}
