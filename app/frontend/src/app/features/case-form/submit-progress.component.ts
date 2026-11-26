import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ProgressStep } from '../../core/model';
import { t } from '../../shared/texts.pl';
import { FORM_LABELS } from './case-form.labels';

const STEPS = [
  { step: 'UPLOAD_RECEIVED', text: t('progress.UPLOAD_RECEIVED') },
  { step: 'ANALYZING_PHOTO', text: t('progress.ANALYZING_PHOTO') },
  { step: 'PREPARING_DECISION', text: t('progress.PREPARING_DECISION') },
] as const;

/** Loading state after submit: three steps, the current one highlighted (AC-14). */
@Component({
  selector: 'app-submit-progress',
  imports: [MatProgressBarModule],
  templateUrl: './submit-progress.component.html',
  styleUrl: './submit-progress.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SubmitProgressComponent {
  readonly step = input<ProgressStep | null>(null);
  protected readonly title = FORM_LABELS.progressTitle;

  protected readonly items = computed(() => {
    const current = this.step();
    // WRITING_REPLY (and anything later) keeps the last step highlighted.
    const index =
      current === null
        ? 0
        : Math.max(
            0,
            STEPS.findIndex((s) => s.step === current),
          );
    const active = current === 'WRITING_REPLY' ? STEPS.length - 1 : index;
    return STEPS.map((s, i) => ({ text: s.text, current: i === active, done: i < active }));
  });
}
