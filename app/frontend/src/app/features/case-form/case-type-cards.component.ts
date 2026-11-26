import { ChangeDetectionStrategy, Component, input, viewChildren } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatRadioButton, MatRadioModule } from '@angular/material/radio';
import { CaseType } from '../../core/model';
import { t } from '../../shared/texts.pl';
import { FORM_LABELS } from './case-form.labels';

/** Two large selectable cards on `mat-radio-group` (radio semantics, arrow-key navigation). */
@Component({
  selector: 'app-case-type-cards',
  imports: [ReactiveFormsModule, MatRadioModule],
  templateUrl: './case-type-cards.component.html',
  styleUrl: './case-type-cards.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CaseTypeCardsComponent {
  readonly control = input.required<FormControl<CaseType | null>>();
  protected readonly legend = FORM_LABELS.caseType;
  protected readonly options: readonly { value: CaseType; title: string; description: string }[] = [
    {
      value: 'COMPLAINT',
      title: t('form.caseType.complaint'),
      description: t('form.caseType.complaintDesc'),
    },
    {
      value: 'RETURN',
      title: t('form.caseType.return'),
      description: t('form.caseType.returnDesc'),
    },
  ];
  private readonly radios = viewChildren(MatRadioButton);

  /** Focuses the first radio (used when the form is submitted without a type). */
  focus(): void {
    this.radios()[0]?.focus();
  }
}
