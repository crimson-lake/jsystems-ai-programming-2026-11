import { TestBed } from '@angular/core/testing';
import { TypingIndicatorComponent } from './typing-indicator.component';
import texts from '../../shared/texts.pl.json';

describe('TypingIndicatorComponent (AC-37)', () => {
  it('shows three dots and a visually hidden "Asystent pisze…"', async () => {
    const fixture = TestBed.createComponent(TypingIndicatorComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('.typing__dot').length).toBe(3);
    const hidden = el.querySelector('.visually-hidden')!;
    expect(hidden.textContent?.trim()).toBe(texts['chat.typing']);
    expect(el.querySelector('.typing__dot')?.getAttribute('aria-hidden')).toBe('true');
  });
});
