import { ComponentFixture, TestBed } from '@angular/core/testing';
import { decisionView, uiMessage } from '../../../testing/fake-case-store';
import { MarkdownPipe } from '../../core/markdown/markdown.pipe';
import { UiMessage } from '../../core/model';
import { AssistantMessageComponent } from './assistant-message.component';
import texts from '../../shared/texts.pl.json';

const BODY = '### Co widzimy na zdjęciu\n\nTekst.\n\n#### Uzasadnienie\n\n- punkt\n\n### Kolejne kroki\n\n1. Krok **pierwszy**\n';

describe('AssistantMessageComponent (ADR-001 3.5 segment order)', () => {
  let fixture: ComponentFixture<AssistantMessageComponent>;
  let el: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  function create(message: UiMessage): void {
    fixture = TestBed.createComponent(AssistantMessageComponent);
    fixture.componentRef.setInput('message', message);
    el = fixture.nativeElement;
  }

  async function render(message: UiMessage): Promise<void> {
    create(message);
    await fixture.whenStable();
  }

  const segments = () =>
    Array.from(el.querySelectorAll('[data-testid]'))
      .map((e) => e.getAttribute('data-testid'))
      .filter((id) => id !== 'assistant-message');
  const first = (decision = {}) =>
    uiMessage({ isFirst: true, text: BODY, decision: decisionView(decision) });

  describe('segments', () => {
    it('first message, no safety: greeting, badge, body, disclaimer', async () => {
      await render(first({ label: 'ACCEPTED_PRELIMINARY' }));
      expect(segments()).toEqual(['assistant-greeting', 'decision-badge', 'disclaimer']);
      expect(el.querySelector('[data-testid=assistant-greeting]')?.textContent?.trim()).toBe(texts['chat.greeting']);
      expect(el.querySelector('[data-testid=decision-badge]')?.textContent?.trim()).toBe(texts['label.ACCEPTED_PRELIMINARY']);
      expect(el.querySelector('[data-testid=disclaimer]')?.textContent?.trim()).toBe(texts['chat.disclaimer']);
    });

    it('first message with safety: safety block is the FIRST element with a test id (AC-26)', async () => {
      await render(first({ label: 'ESCALATED', safety: true }));
      expect(segments()).toEqual(['safety-block', 'assistant-greeting', 'decision-badge', 'disclaimer']);
      const safety = el.querySelector('[data-testid=safety-block]')!;
      expect(safety.textContent).toContain('Ze względów bezpieczeństwa:');
      expect(safety.textContent).toContain('przestań używać urządzenia');
      expect(safety.textContent).not.toContain('**');
      expect(safety.querySelector('strong')?.textContent).toBe('Ze względów bezpieczeństwa:');
    });

    it('the safety block comes before the greeting, badge, body and disclaimer in the DOM', async () => {
      await render(first({ label: 'ESCALATED', safety: true }));
      const order = Array.from(el.querySelectorAll('[data-testid=safety-block], .assistant__body, [data-testid=disclaimer]'));
      expect(order[0].getAttribute('data-testid')).toBe('safety-block');
    });

    it('changed reply: "Zmiana decyzji" block with previous -> new badge and the reason, no greeting', async () => {
      await render(
        uiMessage({
          id: 'a2',
          isFirst: false,
          text: 'Nowa ocena.',
          decision: decisionView({
            changed: true,
            previousLabel: 'NEEDS_MORE_INFO',
            label: 'ACCEPTED_PRELIMINARY',
            changeReasonPl: 'Dodane zdjęcie potwierdza uszkodzenie.',
          }),
        }),
      );
      expect(segments()).toEqual(['decision-change-block', 'decision-badge', 'disclaimer']);
      const block = el.querySelector('[data-testid=decision-change-block]')!;
      const text = block.textContent!;
      expect(text).toContain(texts['chat.decisionChange']);
      expect(text).toContain('Dodane zdjęcie potwierdza uszkodzenie.');
      const badges = Array.from(block.querySelectorAll('.badge')).map((b) => b.textContent?.trim());
      expect(badges).toEqual([texts['label.NEEDS_MORE_INFO'], texts['label.ACCEPTED_PRELIMINARY']]);
    });

    it('changed reply with safety: safety block, then the change block (AC-35)', async () => {
      await render(
        uiMessage({
          isFirst: false,
          text: 'x',
          decision: decisionView({ changed: true, previousLabel: 'ACCEPTED_PRELIMINARY', label: 'ESCALATED', safety: true, changeReasonPl: 'Powód.' }),
        }),
      );
      expect(segments()).toEqual(['safety-block', 'decision-change-block', 'decision-badge', 'disclaimer']);
    });

    it('changed reply without a previous label still renders the block', async () => {
      await render(
        uiMessage({ isFirst: false, text: 'x', decision: decisionView({ changed: true, previousLabel: null, label: 'REJECTED', changeReasonPl: null }) }),
      );
      const block = el.querySelector('[data-testid=decision-change-block]')!;
      expect(block.querySelectorAll('.badge').length).toBe(1);
    });

    it('plain follow-up (not first, not changed): body only, even if the safety flag is set', async () => {
      await render(uiMessage({ id: 'a2', isFirst: false, text: 'Odpowiedź.', decision: decisionView({ safety: true }) }));
      expect(segments()).toEqual([]);
      expect(el.querySelector('.assistant__body')?.textContent).toContain('Odpowiedź.');
    });
  });

  describe('body', () => {
    it('renders the markdown as real headings, lists and bold', async () => {
      await render(first());
      const body = el.querySelector('.assistant__body')!;
      expect(Array.from(body.querySelectorAll('h3')).map((h) => h.textContent)).toEqual(['Co widzimy na zdjęciu', 'Kolejne kroki']);
      expect(body.querySelector('h4')?.textContent).toBe('Uzasadnienie');
      expect(body.querySelector('ol strong')?.textContent).toBe('pierwszy');
    });

    it('sanitizes model output', async () => {
      await render(first());
      fixture.componentRef.setInput('message', { ...first(), text: 'ok <script>alert(1)</script><img src=x onerror=alert(1)> [a](javascript:alert(1))' });
      await fixture.whenStable();
      const html = el.querySelector('.assistant__body')!.innerHTML;
      expect(html).not.toContain('<script');
      expect(html).not.toContain('<img');
      expect(html).not.toContain('javascript:');
    });

    it('the body carries no test id (it would break the safety-first contract)', async () => {
      await render(first());
      expect(el.querySelector('.assistant__body')?.hasAttribute('data-testid')).toBe(false);
    });
  });

  describe('state', () => {
    it.each([
      ['streaming', 'streaming'],
      ['complete', 'complete'],
      ['failed', 'error'],
      ['sending', 'streaming'],
    ] as const)('status %s sets data-state=%s on the message container', async (status, state) => {
      await render({ ...first(), status });
      const root = el.querySelector('[data-testid=assistant-message]')!;
      expect(root.getAttribute('data-state')).toBe(state);
    });
  });

  describe('throttled markdown (ADR-003 3.6)', () => {
    beforeEach(() => {
      vi.useFakeTimers({ toFake: ['requestAnimationFrame', 'cancelAnimationFrame'] });
    });
    afterEach(() => {
      vi.useRealTimers();
      vi.restoreAllMocks();
    });

    const text = () => el.querySelector('.assistant__body')?.textContent?.trim();
    const streaming = (t: string): UiMessage => ({ ...first(), status: 'streaming', text: t });

    it('re-parses at most once per animation frame while streaming', () => {
      const spy = vi.spyOn(MarkdownPipe.prototype, 'transform');
      create(streaming('A'));
      fixture.detectChanges();
      expect(text()).toBe('A');
      const afterFirst = spy.mock.calls.length;

      for (const t of ['AB', 'ABC', 'ABCD']) {
        fixture.componentRef.setInput('message', streaming(t));
        fixture.detectChanges();
      }
      expect(text()).toBe('A');

      vi.advanceTimersToNextFrame();
      fixture.detectChanges();
      expect(text()).toBe('ABCD');
      const bodyParses = spy.mock.calls.slice(afterFirst).filter(([s]) => (s ?? '').startsWith('AB'));
      expect(bodyParses.length).toBe(1);
    });

    it('shows the final text immediately when the message completes', () => {
      create(streaming('A'));
      fixture.detectChanges();
      fixture.componentRef.setInput('message', { ...streaming('ABC'), status: 'complete' });
      fixture.detectChanges();
      expect(text()).toBe('ABC');
    });
  });
});
