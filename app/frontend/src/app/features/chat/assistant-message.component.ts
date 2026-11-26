import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { MarkdownPipe } from '../../core/markdown/markdown.pipe';
import { UiMessage } from '../../core/model';
import { DecisionBadgeComponent } from '../../shared/decision-badge.component';
import { t } from '../../shared/texts.pl';

/**
 * Agent bubble. Only the body is model text; the other segments are fixed UI in the order of
 * ADR-001 3.5: safety block, "Zmiana decyzji", greeting, badge, body, disclaimer.
 */
@Component({
  selector: 'app-assistant-message',
  imports: [DatePipe, DecisionBadgeComponent, MarkdownPipe],
  templateUrl: './assistant-message.component.html',
  styleUrl: './assistant-message.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssistantMessageComponent {
  readonly message = input.required<UiMessage>();

  protected readonly safetyText = t('chat.safety');
  protected readonly greetingText = t('chat.greeting');
  protected readonly changeTitle = t('chat.decisionChange');
  protected readonly disclaimerText = t('chat.disclaimer');

  private readonly decision = computed(() => this.message().decision);
  /** Only when the message is the first one or a changed reply (AC-26, AC-35). */
  protected readonly showSafety = computed(() => {
    const d = this.decision();
    return !!d?.safety && (this.message().isFirst || d.changed);
  });
  protected readonly changed = computed(() => !!this.decision()?.changed);
  protected readonly showBadge = computed(() => !!this.decision() && (this.message().isFirst || this.changed()));
  protected readonly state = computed(() => {
    switch (this.message().status) {
      case 'complete':
        return 'complete';
      case 'failed':
        return 'error';
      default:
        return 'streaming';
    }
  });

  /** Text handed to the markdown pipe: at most one new value per animation frame while streaming. */
  protected readonly shownText = signal('');

  constructor() {
    let frame: number | undefined;
    let first = true;
    const cancel = (): void => {
      if (frame !== undefined && typeof cancelAnimationFrame === 'function') cancelAnimationFrame(frame);
      frame = undefined;
    };
    effect(() => {
      const { text, status } = this.message();
      untracked(() => {
        const streaming = status === 'streaming' || status === 'sending';
        if (first || !streaming || typeof requestAnimationFrame !== 'function') {
          first = false;
          cancel();
          this.shownText.set(text);
        } else if (frame === undefined) {
          frame = requestAnimationFrame(() => {
            frame = undefined;
            this.shownText.set(this.message().text);
          });
        }
      });
    });
    inject(DestroyRef).onDestroy(cancel);
  }
}
