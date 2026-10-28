import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'cargar' },
  {
    path: 'cargar',
    loadComponent: () => import('./pages/upload/upload.component').then((module) => module.UploadComponent)
  },
  {
    path: 'buscar',
    loadComponent: () => import('./pages/search/search.component').then((module) => module.SearchComponent)
  }
];
