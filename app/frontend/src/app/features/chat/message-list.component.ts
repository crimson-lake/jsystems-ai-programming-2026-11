import { LiveAnnouncer } from '@angular/cdk/a11y';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  afterRenderEffect,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { CaseStore } from '../../core/state/case.store';
import { t } from '../../shared/texts.pl';
import { AssistantMessageComponent } from './assistant-message.component';
import { TypingIndicatorComponent } from './typing-indicator.component';
import { UserMessageComponent } from './user-message.component';

/** Follow the newest content only while the customer is this close to the bottom (AC-39). */
export const PIN_THRESHOLD_PX = 80;
/** Space kept above the start of a long reply when following it. */
const REPLY_TOP_MARGIN_PX = 8;

/**
 * Message container: bubbles in store order plus the typing indicator, in a scroll region that
 * follows new content while pinned to the bottom, otherwise offers "Nowe wiadomości ↓" (AC-39), and
 * announces completed replies politely (ADR-003 3.5).
 */
@Component({
  selector: 'app-message-list',
  imports: [MatButtonModule, AssistantMessageComponent, UserMessageComponent, TypingIndicatorComponent],
  template: `
    <div class="message-list__wrap">
      <section #scroller class="message-list" tabindex="0" [attr.aria-label]="label" (scroll)="onScroll()">
        <div #content class="message-list__content">
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
        </div>
      </section>
      @if (showNewMessages()) {
        <button mat-flat-button class="message-list__new" type="button" (click)="jumpToBottom()">
          {{ newMessagesText }}
        </button>
      }
    </div>
  `,
  styleUrl: './message-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MessageListComponent {
  protected readonly store = inject(CaseStore);
  private readonly announcer = inject(LiveAnnouncer);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly label = t('chat.list.label');
  protected readonly newMessagesText = t('chat.newMessages');

  private readonly scroller = viewChild.required<ElementRef<HTMLElement>>('scroller');
  private readonly content = viewChild.required<ElementRef<HTMLElement>>('content');

  protected readonly showNewMessages = signal(false);

  /** From send until the first delta: the last message is the customer's or an empty agent shell. */
  protected readonly typing = computed(() => {
    if (!this.store.streaming()) return false;
    const last = this.store.messages().at(-1);
    return !last || last.role === 'user' || last.text === '';
  });

  private pinned = true;
  private seenCount = 0;
  private seenTextLength = 0;
  private lastHeight = 0;
  private readonly streamingIds = new Set<string>();

  constructor() {
    // After each render: new or longer messages follow the bottom when pinned, else raise the button.
    afterRenderEffect(() => {
      const messages = this.store.messages();
      const last = messages.at(-1);
      const textLength = last?.text.length ?? 0;
      const grew =
        messages.length > this.seenCount ||
        (messages.length === this.seenCount && textLength > this.seenTextLength);
      // Sending a message always brings it into view.
      if (messages.length > this.seenCount && last?.role === 'user') this.pinned = true;
      this.seenCount = messages.length;
      this.seenTextLength = textLength;
      if (grew) this.onContentChanged();
    });

    // Content that grows later than the signal (markdown is re-rendered once per frame).
    afterNextRender(() => {
      if (typeof ResizeObserver === 'undefined') return;
      const observer = new ResizeObserver(() => {
        const height = this.scroller().nativeElement.scrollHeight;
        const grew = height > this.lastHeight;
        this.lastHeight = height;
        if (grew) this.onContentChanged();
      });
      observer.observe(this.content().nativeElement);
      this.destroyRef.onDestroy(() => observer.disconnect());
    });

    effect(() => {
      for (const message of this.store.messages()) {
        if (message.role !== 'assistant') continue;
        if (message.status === 'streaming') this.streamingIds.add(message.id);
        else if (message.status === 'complete' && this.streamingIds.delete(message.id)) {
          void this.announcer.announce(t('chat.announce.reply'), 'polite');
        }
      }
    });
  }

  protected onScroll(): void {
    this.pinned = this.distanceFromBottom() <= PIN_THRESHOLD_PX;
    if (this.pinned) this.showNewMessages.set(false);
  }

  protected jumpToBottom(): void {
    this.pinned = true;
    this.scrollToBottom(false);
    this.showNewMessages.set(false);
  }

  private onContentChanged(): void {
    if (this.pinned) {
      this.scrollToBottom();
    } else {
      this.showNewMessages.set(true);
    }
  }

  private distanceFromBottom(): number {
    const el = this.scroller().nativeElement;
    return el.scrollHeight - el.clientHeight - el.scrollTop;
  }

  /**
   * Follows the newest content, but never past the start of the latest assistant reply when that reply is taller
   * than the viewport, so its safety block, badge and first section stay readable (AC-22, AC-26; Q3 finding).
   * The "Nowe wiadomości" button passes {@code capAtReplyStart = false} and goes to the very end.
   */
  private scrollToBottom(capAtReplyStart = true): void {
    const el = this.scroller().nativeElement;
    let target = el.scrollHeight;
    if (capAtReplyStart && this.store.messages().at(-1)?.role === 'assistant') {
      const replies = el.querySelectorAll<HTMLElement>('[data-testid="assistant-message"]');
      const reply = replies.item(replies.length - 1);
      if (reply) {
        const box = reply.getBoundingClientRect();
        if (box.height > el.clientHeight) {
          const start = box.top - el.getBoundingClientRect().top + el.scrollTop;
          target = Math.min(target, Math.max(0, start - REPLY_TOP_MARGIN_PX));
        }
      }
    }
    el.scrollTop = target;
    this.lastHeight = el.scrollHeight;
  }
}
