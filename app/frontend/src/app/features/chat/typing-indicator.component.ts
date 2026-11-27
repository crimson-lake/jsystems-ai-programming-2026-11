import { ChangeDetectionStrategy, Component } from '@angular/core';
import { t } from '../../shared/texts.pl';

/** Three animated dots until the first delta (AC-37); the text is for screen readers only. */
@Component({
  selector: 'app-typing-indicator',
  template: `
    <div class="typing">
      <span class="typing__dot" aria-hidden="true"></span>
      <span class="typing__dot" aria-hidden="true"></span>
      <span class="typing__dot" aria-hidden="true"></span>
      <span class="visually-hidden">{{ text }}</span>
    </div>
  `,
  styleUrl: './typing-indicator.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TypingIndicatorComponent {
  protected readonly text = t('chat.typing');
}
