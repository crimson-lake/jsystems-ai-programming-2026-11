import { LiveAnnouncer } from '@angular/cdk/a11y';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FakeCaseStore, decisionView, uiMessage } from '../../../testing/fake-case-store';
import { CaseStore } from '../../core/state/case.store';
import { MessageListComponent } from './message-list.component';
import texts from '../../shared/texts.pl.json';
import { NewCaseService } from './new-case.service';

describe('MessageListComponent', () => {
  let store: FakeCaseStore;
  let fixture: ComponentFixture<MessageListComponent>;
  let el: HTMLElement;
  let announce: ReturnType<typeof vi.fn>;
  let observers: { callback: () => void }[];

  beforeEach(async () => {
    store = new FakeCaseStore();
    announce = vi.fn();
    observers = [];
    vi.stubGlobal(
      'ResizeObserver',
      class {
        constructor(callback: () => void) {
          observers.push({ callback });
        }
        observe = () => undefined;
        disconnect = () => undefined;
      },
    );
    TestBed.configureTestingModule({
      providers: [
        { provide: LiveAnnouncer, useValue: { announce } },
        { provide: CaseStore, useValue: store },
        { provide: NewCaseService, useValue: { start: () => undefined } },
      ],
    });
    fixture = TestBed.createComponent(MessageListComponent);
    el = fixture.nativeElement;
    await fixture.whenStable();
  });

  const user = (id: string, text: string) =>
    uiMessage({ id, role: 'user', text, isFirst: false, status: 'complete' });
  const assistant = (id: string, text: string, status: 'streaming' | 'complete' = 'complete') =>
    uiMessage({ id, text, status, isFirst: id === 'a1', decision: decisionView() });
  const typing = () => el.querySelector('app-typing-indicator');

  it('renders messages in store order, by role', async () => {
    store.messages.set([assistant('a1', 'Pierwsza'), user('u1', 'Pytanie'), assistant('a2', 'Druga')]);
    await fixture.whenStable();
    const kinds = Array.from(el.querySelectorAll('app-assistant-message, app-user-message')).map((e) =>
      e.tagName.toLowerCase(),
    );
    expect(kinds).toEqual(['app-assistant-message', 'app-user-message', 'app-assistant-message']);
    expect(el.querySelectorAll('[data-testid=assistant-message]').length).toBe(2);
  });

  it('has a labelled scroll container', () => {
    const list = el.querySelector('.message-list')!;
    expect(list.getAttribute('aria-label')).toBeTruthy();
  });

  it('shows the typing indicator from send until the first delta (AC-37)', async () => {
    store.messages.set([assistant('a1', 'Pierwsza'), user('u2', 'Pytanie')]);
    store.streaming.set(true);
    await fixture.whenStable();
    expect(typing()).toBeTruthy();

    // `decision` arrived: assistant shell without text yet.
    store.messages.set([assistant('a1', 'Pierwsza'), user('u2', 'Pytanie'), assistant('a3', '', 'streaming')]);
    await fixture.whenStable();
    expect(typing()).toBeTruthy();

    // first delta
    store.messages.set([assistant('a1', 'Pierwsza'), user('u2', 'Pytanie'), assistant('a3', 'Od', 'streaming')]);
    await fixture.whenStable();
    expect(typing()).toBeNull();
  });

  it('shows no typing indicator when nothing is streaming', async () => {
    store.messages.set([assistant('a1', 'Pierwsza'), user('u2', 'Pytanie')]);
    store.streaming.set(false);
    await fixture.whenStable();
    expect(typing()).toBeNull();
  });

  describe('scrolling (AC-39, ADR-003 3.5)', () => {
    let scroller: HTMLElement;
    let layout: { scrollHeight: number; clientHeight: number };

    beforeEach(async () => {
      scroller = el.querySelector<HTMLElement>('.message-list')!;
      layout = { scrollHeight: 400, clientHeight: 300 };
      // jsdom has no layout: the scroll geometry is injected.
      Object.defineProperty(scroller, 'scrollHeight', { get: () => layout.scrollHeight, configurable: true });
      Object.defineProperty(scroller, 'clientHeight', { get: () => layout.clientHeight, configurable: true });
      Object.defineProperty(scroller, 'scrollTop', { value: 0, writable: true, configurable: true });
      store.messages.set([assistant('a1', 'Pierwsza')]);
      await fixture.whenStable();
    });

    const newMessages = () =>
      Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes(texts['chat.newMessages']));
    const scrollTo = async (top: number) => {
      scroller.scrollTop = top;
      scroller.dispatchEvent(new Event('scroll'));
      await fixture.whenStable();
    };
    const grow = async (by: number, text: string) => {
      layout.scrollHeight += by;
      store.messages.set([assistant('a1', text, 'streaming')]);
      await fixture.whenStable();
    };

    it('scrolls to the bottom and shows no button while pinned at the bottom', async () => {
      await scrollTo(100); // distance 0
      await grow(50, 'Pierwsza odpowiedź');
      expect(scroller.scrollTop).toBe(layout.scrollHeight);
      expect(newMessages()).toBeUndefined();
    });

    it('still follows when the customer is exactly 80 px from the bottom', async () => {
      await scrollTo(20); // 400 - 300 - 20 = 80
      await grow(50, 'Dłuższa odpowiedź');
      expect(scroller.scrollTop).toBe(layout.scrollHeight);
      expect(newMessages()).toBeUndefined();
    });

    it('does not jump when scrolled further up and shows "Nowe wiadomości ↓"', async () => {
      await scrollTo(19); // distance 81
      await grow(50, 'Dłuższa odpowiedź');
      expect(scroller.scrollTop).toBe(19);
      expect(newMessages()).toBeDefined();
    });

    it('keeps the start of a reply taller than the viewport in view (safety block, badge; Q3 finding)', async () => {
      await scrollTo(100); // pinned
      const message = el.querySelector<HTMLElement>('[data-testid="assistant-message"]')!;
      // The reply starts 40 px below the top of the scrolled content and is 500 px tall (viewport 300).
      vi.spyOn(scroller, 'getBoundingClientRect').mockReturnValue(new DOMRect(0, 0, 300, 300));
      vi.spyOn(message, 'getBoundingClientRect').mockReturnValue(new DOMRect(0, 40 - 100, 300, 500));
      await grow(400, 'Bardzo długa odpowiedź');
      expect(scroller.scrollTop).toBe(40 - 8);
    });

    it('the button scrolls to the bottom and disappears', async () => {
      await scrollTo(0);
      await grow(200, 'Dużo nowego tekstu');
      newMessages()!.click();
      await fixture.whenStable();
      expect(scroller.scrollTop).toBe(layout.scrollHeight);
      expect(newMessages()).toBeUndefined();
    });

    it('hides the button when the customer scrolls back to the bottom by hand', async () => {
      await scrollTo(0);
      await grow(200, 'Dużo nowego tekstu');
      expect(newMessages()).toBeDefined();
      await scrollTo(layout.scrollHeight - layout.clientHeight);
      expect(newMessages()).toBeUndefined();
    });

    it('shows the button for content that grows after render (throttled markdown) when scrolled up', async () => {
      await scrollTo(0);
      layout.scrollHeight += 120;
      observers.forEach((o) => o.callback());
      await fixture.whenStable();
      expect(scroller.scrollTop).toBe(0);
      expect(newMessages()).toBeDefined();
    });

    it('follows content that grows after render when pinned', async () => {
      await scrollTo(100);
      layout.scrollHeight += 120;
      observers.forEach((o) => o.callback());
      await fixture.whenStable();
      expect(scroller.scrollTop).toBe(layout.scrollHeight);
      expect(newMessages()).toBeUndefined();
    });

    it('does not offer the button for a status change without new content', async () => {
      await scrollTo(0);
      store.messages.set([assistant('a1', 'Pierwsza', 'complete')]);
      await fixture.whenStable();
      expect(newMessages()).toBeUndefined();
    });

    it('always jumps to the bottom when the customer sends a message, even from further up', async () => {
      await scrollTo(0);
      layout.scrollHeight += 60;
      store.messages.set([assistant('a1', 'Pierwsza'), user('u2', 'Moje pytanie')]);
      await fixture.whenStable();
      expect(scroller.scrollTop).toBe(layout.scrollHeight);
      expect(newMessages()).toBeUndefined();
    });

    it('is a keyboard-focusable scroll region', () => {
      expect(scroller.getAttribute('tabindex')).toBe('0');
    });
  });

  describe('LiveAnnouncer', () => {
    it('announces politely when a streaming reply completes', async () => {
      store.messages.set([assistant('a1', 'Odp', 'streaming')]);
      await fixture.whenStable();
      expect(announce).not.toHaveBeenCalled();
      store.messages.set([assistant('a1', 'Odpowiedź', 'complete')]);
      await fixture.whenStable();
      expect(announce).toHaveBeenCalledExactlyOnceWith(texts['chat.announce.reply'], 'polite');
    });

    it('does not announce messages that were already complete, nor the same completion twice', async () => {
      store.messages.set([assistant('a1', 'Gotowa')]);
      await fixture.whenStable();
      store.messages.set([assistant('a1', 'Gotowa'), user('u2', 'Pytanie')]);
      await fixture.whenStable();
      expect(announce).not.toHaveBeenCalled();

      store.messages.set([assistant('a1', 'Gotowa'), user('u2', 'Pytanie'), assistant('a3', 'x', 'streaming')]);
      await fixture.whenStable();
      store.messages.set([assistant('a1', 'Gotowa'), user('u2', 'Pytanie'), assistant('a3', 'xy', 'complete')]);
      await fixture.whenStable();
      store.messages.set([assistant('a1', 'Gotowa'), user('u2', 'Pytanie'), assistant('a3', 'xy', 'complete')]);
      await fixture.whenStable();
      expect(announce).toHaveBeenCalledTimes(1);
    });

    it('does not announce a discarded partial reply', async () => {
      store.messages.set([assistant('a1', 'x', 'streaming')]);
      await fixture.whenStable();
      store.messages.set([]);
      await fixture.whenStable();
      expect(announce).not.toHaveBeenCalled();
    });
  });
});
