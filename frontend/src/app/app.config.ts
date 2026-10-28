import { registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es';
import { ApplicationConfig, LOCALE_ID } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';

registerLocaleData(localeEs);

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(),
    { provide: LOCALE_ID, useValue: 'es' }
  ]
};
