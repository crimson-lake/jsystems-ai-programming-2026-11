import { Routes } from '@angular/router';
import { hasActiveCaseGuard } from './core/guards/has-active-case.guard';

// ADR-003 §3.3: pages are lazy-loaded so the initial bundle stays small.
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/case-form/case-form.page').then((m) => m.CaseFormPage),
  },
  {
    path: 'sprawa',
    canActivate: [hasActiveCaseGuard],
    loadComponent: () => import('./features/chat/chat.page').then((m) => m.ChatPage),
  },
  { path: '**', redirectTo: '' },
];
