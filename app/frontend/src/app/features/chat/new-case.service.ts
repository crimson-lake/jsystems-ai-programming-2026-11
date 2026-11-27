import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { CaseApiService } from '../../core/api/case-api.service';
import { CaseStore } from '../../core/state/case.store';
import { NewCaseDialogComponent } from './new-case-dialog.component';

/** "Nowa sprawa" (AC-44): DELETE the case, reset the store, back to the empty form. */
@Injectable({ providedIn: 'root' })
export class NewCaseService {
  private readonly store = inject(CaseStore);
  private readonly api = inject(CaseApiService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  /** Asks for confirmation first (PRD 9.3); a cancel or Escape changes nothing. */
  confirmAndStart(): void {
    this.dialog
      .open<NewCaseDialogComponent, void, boolean>(NewCaseDialogComponent)
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) this.start();
      });
  }

  /** Without a dialog: also used when the case already expired and there is nothing left to lose. */
  start(): void {
    const caseId = this.store.caseId();
    // Fire and forget: a failed DELETE must not keep the customer in a dead case.
    if (caseId) this.api.deleteCase(caseId).subscribe({ error: () => undefined });
    this.store.reset();
    void this.router.navigateByUrl('');
  }
}
