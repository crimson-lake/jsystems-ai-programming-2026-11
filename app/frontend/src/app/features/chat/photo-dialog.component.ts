import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { t } from '../../shared/texts.pl';

export interface PhotoDialogData {
  url: string;
}

/** Enlarged view of a case or chat photo. */
@Component({
  selector: 'app-photo-dialog',
  imports: [MatButtonModule, MatDialogModule],
  template: `
    <mat-dialog-content>
      <img class="photo" [src]="data.url" [alt]="alt" />
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close cdkFocusInitial>{{ close }}</button>
    </mat-dialog-actions>
  `,
  styles: `
    .photo {
      display: block;
      max-width: 100%;
      max-height: 70vh;
      margin: 0 auto;
      object-fit: contain;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PhotoDialogComponent {
  protected readonly data = inject<PhotoDialogData>(MAT_DIALOG_DATA);
  protected readonly alt = t('form.label.photo');
  protected readonly close = t('common.close');
}
