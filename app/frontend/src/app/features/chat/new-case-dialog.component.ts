import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { t } from '../../shared/texts.pl';

/** Confirmation of "Nowa sprawa" (PRD 9.3). Closes with `true` only on confirm. */
@Component({
  selector: 'app-new-case-dialog',
  imports: [MatButtonModule, MatDialogModule],
  template: `
    <mat-dialog-content>{{ text }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" cdkFocusInitial [mat-dialog-close]="false">
        {{ cancel }}
      </button>
      <button mat-flat-button class="app-secondary" type="button" [mat-dialog-close]="true">
        {{ confirm }}
      </button>
    </mat-dialog-actions>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NewCaseDialogComponent {
  protected readonly text = t('dialog.newCase.text');
  protected readonly cancel = t('dialog.newCase.cancel');
  protected readonly confirm = t('dialog.newCase.confirm');
}
