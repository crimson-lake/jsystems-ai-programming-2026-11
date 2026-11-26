import { Routes } from '@angular/router';

// ADR-003 §3.3: pages are lazy-loaded so the initial bundle stays small; 'sprawa' (ChatPage + guard) is added in T33.
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/case-form/case-form.page').then((m) => m.CaseFormPage),
  },
  { path: '**', redirectTo: '' },
];
