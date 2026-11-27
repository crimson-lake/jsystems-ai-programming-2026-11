import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { CATEGORY_LABELS } from '../../core/model';
import { CaseStore } from '../../core/state/case.store';
import { FORM_LABELS } from '../case-form/case-form.labels';
import { DecisionBadgeComponent } from '../../shared/decision-badge.component';
import { t } from '../../shared/texts.pl';
import { NewCaseService } from './new-case.service';
import { PhotoDialogComponent } from './photo-dialog.component';

/** Case summary panel (PRD 9.2): type, category, model, date, photo, current decision, new case. */
@Component({
  selector: 'app-case-summary',
  imports: [DatePipe, MatButtonModule, DecisionBadgeComponent],
  templateUrl: './case-summary.component.html',
  styleUrl: './case-summary.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CaseSummaryComponent {
  /** The wide layout shows a heading; the narrow one has the expansion panel header instead. */
  readonly showTitle = input(false);

  protected readonly store = inject(CaseStore);
  private readonly newCase = inject(NewCaseService);
  private readonly dialog = inject(MatDialog);

  protected readonly labels = FORM_LABELS;
  protected readonly title = t('chat.summaryTitle');
  protected readonly photoAlt = t('form.label.photo');
  protected readonly enlargeLabel = t('chat.photo.enlarge');
  protected readonly newCaseText = t('common.newCase');

  protected readonly caseTypeText = computed(() =>
    this.store.form()?.caseType === 'COMPLAINT'
      ? t('form.caseType.complaint')
      : t('form.caseType.return'),
  );
  protected readonly categoryText = computed(() => {
    const category = this.store.form()?.category;
    return category ? CATEGORY_LABELS[category] : '';
  });

  protected enlarge(): void {
    const url = this.store.formPhoto()?.objectUrl;
    if (url) this.dialog.open(PhotoDialogComponent, { data: { url }, maxWidth: '92vw' });
  }

  protected startNewCase(): void {
    this.newCase.confirmAndStart();
  }
}
