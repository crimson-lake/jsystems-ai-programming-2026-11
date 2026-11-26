import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { CaseStore } from '../../core/state/case.store';
import { t } from '../../shared/texts.pl';
import { AssistantMessageComponent } from './assistant-message.component';
import { TypingIndicatorComponent } from './typing-indicator.component';
import { UserMessageComponent } from './user-message.component';

/** Message container: bubbles in store order plus the typing indicator. Scroll logic is T34. */
@Component({
  selector: 'app-message-list',
  imports: [AssistantMessageComponent, UserMessageComponent, TypingIndicatorComponent],
  template: `
    <section class="message-list" [attr.aria-label]="label">
      @for (message of store.messages(); track message.id) {
        @if (message.role === 'user') {
          <app-user-message [message]="message" />
        } @else {
          <app-assistant-message [message]="message" />
        }
      }
      @if (typing()) {
        <app-typing-indicator />
      }
    </section>
  `,
  styleUrl: './message-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MessageListComponent {
  protected readonly store = inject(CaseStore);
  protected readonly label = t('chat.list.label');

  /** From send until the first delta: the last message is the customer's or an empty agent shell. */
  protected readonly typing = computed(() => {
    if (!this.store.streaming()) return false;
    const last = this.store.messages().at(-1);
    return !last || last.role === 'user' || last.text === '';
  });
}
