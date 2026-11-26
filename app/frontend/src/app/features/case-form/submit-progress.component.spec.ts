import { TestBed } from '@angular/core/testing';
import { ProgressStep } from '../../core/model';
import { SubmitProgressComponent } from './submit-progress.component';
import texts from '../../shared/texts.pl.json';

describe('SubmitProgressComponent', () => {
  async function render(step: ProgressStep | null): Promise<HTMLElement> {
    await TestBed.configureTestingModule({
      imports: [SubmitProgressComponent],
    }).compileComponents();
    const fixture = TestBed.createComponent(SubmitProgressComponent);
    fixture.componentRef.setInput('step', step);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }
  const items = (el: HTMLElement) => Array.from(el.querySelectorAll('li'));

  it('lists the three steps in order', async () => {
    const el = await render(null);
    expect(items(el).map((li) => li.textContent?.trim())).toEqual([
      texts['progress.UPLOAD_RECEIVED'],
      texts['progress.ANALYZING_PHOTO'],
      texts['progress.PREPARING_DECISION'],
    ]);
  });

  it('highlights the first step before any progress event', async () => {
    const el = await render(null);
    expect(items(el).map((li) => li.getAttribute('aria-current'))).toEqual(['step', null, null]);
  });

  it('highlights the current step and marks earlier ones done', async () => {
    const el = await render('ANALYZING_PHOTO');
    expect(items(el).map((li) => li.getAttribute('aria-current'))).toEqual([null, 'step', null]);
    expect(items(el).map((li) => li.classList.contains('is-done'))).toEqual([true, false, false]);
  });

  it('keeps the last step for WRITING_REPLY', async () => {
    const el = await render('WRITING_REPLY');
    expect(items(el).map((li) => li.getAttribute('aria-current'))).toEqual([null, null, 'step']);
  });
});
