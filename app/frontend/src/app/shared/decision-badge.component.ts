import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecisionLabel } from '../core/model';
import { t } from './texts.pl';

const MODIFIERS: Readonly<Record<DecisionLabel, string>> = {
  ACCEPTED_PRELIMINARY: 'accepted',
  REJECTED: 'rejected',
  NEEDS_MORE_INFO: 'needs-info',
  ESCALATED: 'escalated',
};

/** Decision label as Polish text on the `--app-badge-*` colours (ADR-003 3.2). */
@Component({
  selector: 'app-decision-badge',
  template: `<span class="badge" [class]="'badge badge--' + modifier()">{{ text() }}</span>`,
  styleUrl: './decision-badge.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DecisionBadgeComponent {
  readonly label = input.required<DecisionLabel>();
  protected readonly text = computed(() => t(`label.${this.label()}`));
  protected readonly modifier = computed(() => MODIFIERS[this.label()]);
}
