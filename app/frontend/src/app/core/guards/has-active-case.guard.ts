import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { CaseStore } from '../state/case.store';

/** The chat needs a case in memory; after a reload there is none, so go to the empty form (AC-43). */
export const hasActiveCaseGuard: CanActivateFn = () => {
  const store = inject(CaseStore);
  return store.caseId() !== null ? true : inject(Router).parseUrl('/');
};
