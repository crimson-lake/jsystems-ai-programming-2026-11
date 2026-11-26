import { LOCALE_ID, signal } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localePl from '@angular/common/locales/pl';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MAT_DATE_LOCALE } from '@angular/material/core';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatDatepickerInputHarness } from '@angular/material/datepicker/testing';
import { MatInputHarness } from '@angular/material/input/testing';
import { MatRadioGroupHarness } from '@angular/material/radio/testing';
import { MatSelectHarness } from '@angular/material/select/testing';
import { provideDateFnsAdapter } from '@angular/material-date-fns-adapter';
import { pl } from 'date-fns/locale';
import { CaseFormValue, ProgressStep, ReducedPhoto } from '../../core/model';
import { CaseFormPage } from './case-form.page';
import { CaseSubmitter } from './case-submitter';
import texts from '../../shared/texts.pl.json';

registerLocaleData(localePl);

/** Submitter that already holds a failed first submit, as the store does after AC-40. */
class RestoringSubmitter extends CaseSubmitter {
  readonly progressStep = signal<ProgressStep | null>(null);
  readonly submitting = signal(false);
  readonly submitError = signal(true);
  override readonly form = signal<CaseFormValue | null>(null);
  override readonly formPhoto = signal<ReducedPhoto | null>(null);
  readonly calls: { form: CaseFormValue; photo: ReducedPhoto }[] = [];
  submit(form: CaseFormValue, photo: ReducedPhoto): void {
    this.calls.push({ form, photo });
  }
}

const SAVED: CaseFormValue = {
  caseType: 'COMPLAINT',
  category: 'LAPTOP',
  model: 'ThinkPad T14',
  purchaseDate: new Date(2026, 10, 6),
  reason: 'Urządzenie samo się wyłącza.',
  photo: new File(['x'], 'laptop-1.png', { type: 'image/png' }),
};
const SAVED_PHOTO: ReducedPhoto = {
  blob: new Blob(['jpeg'], { type: 'image/jpeg' }),
  objectUrl: 'blob:kept-photo',
  originalName: 'laptop-1.png',
  width: 100,
  height: 100,
};

describe('CaseFormPage restore after a failed first submit (AC-40)', () => {
  let submitter: RestoringSubmitter;
  let fixture: ComponentFixture<CaseFormPage>;
  let loader: HarnessLoader;
  let el: HTMLElement;

  async function setup(prefill: boolean): Promise<void> {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 10, 26, 12, 0, 0));
    submitter = new RestoringSubmitter();
    if (prefill) {
      submitter.form.set(SAVED);
      submitter.formPhoto.set(SAVED_PHOTO);
    }
    TestBed.configureTestingModule({
      imports: [CaseFormPage],
      providers: [
        provideDateFnsAdapter(),
        { provide: MAT_DATE_LOCALE, useValue: pl },
        { provide: LOCALE_ID, useValue: 'pl' },
        { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { appearance: 'outline' } },
        { provide: CaseSubmitter, useValue: submitter },
      ],
    });
    fixture = TestBed.createComponent(CaseFormPage);
    el = fixture.nativeElement;
    loader = TestbedHarnessEnvironment.loader(fixture);
    await fixture.whenStable();
  }

  afterEach(() => vi.useRealTimers());

  describe('with values kept in the store', () => {
    beforeEach(() => setup(true));

    it('pre-fills every field', async () => {
      const group = await loader.getHarness(MatRadioGroupHarness);
      expect(await group.getCheckedValue()).toBe('COMPLAINT');
      expect(await (await loader.getHarness(MatSelectHarness)).getValueText()).toBe('Laptop');
      expect(await (await loader.getHarness(MatInputHarness.with({ placeholder: texts['form.model.placeholder'] }))).getValue()).toBe('ThinkPad T14');
      expect(await (await loader.getHarness(MatDatepickerInputHarness)).getValue()).toBe('06.11.2026');
      expect(await (await loader.getHarness(MatInputHarness.with({ selector: 'textarea' }))).getValue()).toBe('Urządzenie samo się wyłącza.');
    });

    it('keeps the photo thumbnail and file name', () => {
      const thumb = el.querySelector<HTMLImageElement>('[data-testid="photo-thumbnail"]')!;
      expect(thumb.getAttribute('src')).toBe('blob:kept-photo');
      expect(el.textContent).toContain('laptop-1.png');
    });

    it('shows the submit-error banner with "Spróbuj ponownie"', async () => {
      expect(el.querySelector('.case-form__banner')?.textContent).toContain(texts['form.submitError']);
      await loader.getHarness(MatButtonHarness.with({ text: texts['common.retry'] }));
    });

    it('retry re-sends the restored values and the kept reduced photo', async () => {
      const retry = await loader.getHarness(MatButtonHarness.with({ text: texts['common.retry'] }));
      await retry.click();
      expect(submitter.calls).toHaveLength(1);
      expect(submitter.calls[0].form).toEqual(SAVED);
      expect(submitter.calls[0].photo).toBe(SAVED_PHOTO);
    });

    it('the restored form is valid: submitting sends the restored values without re-picking the photo', async () => {
      const submit = await loader.getHarness(MatButtonHarness.with({ text: texts['form.submit'] }));
      await submit.click();
      expect(submitter.calls).toHaveLength(1);
      expect(submitter.calls[0].form.model).toBe('ThinkPad T14');
      expect(submitter.calls[0].photo).toBe(SAVED_PHOTO);
    });
  });

  describe('with an empty store', () => {
    beforeEach(() => setup(false));

    it('starts with an empty form and no thumbnail', async () => {
      const group = await loader.getHarness(MatRadioGroupHarness);
      expect(await group.getCheckedValue()).toBeNull();
      expect(await (await loader.getHarness(MatInputHarness.with({ placeholder: texts['form.model.placeholder'] }))).getValue()).toBe('');
      expect(el.querySelector('[data-testid="photo-thumbnail"]')).toBeNull();
    });
  });
});
