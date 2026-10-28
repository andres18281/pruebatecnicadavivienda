import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'cargar' },
  {
    path: 'cargar',
    loadComponent: () => import('./pages/upload/upload.component').then((module) => module.UploadComponent)
  }
];
