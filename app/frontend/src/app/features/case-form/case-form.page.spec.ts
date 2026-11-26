import { LOCALE_ID, signal } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localePl from '@angular/common/locales/pl';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MAT_DATE_LOCALE } from '@angular/material/core';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatDatepickerInputHarness } from '@angular/material/datepicker/testing';
import { MatFormFieldHarness } from '@angular/material/form-field/testing';
import { MatInputHarness } from '@angular/material/input/testing';
import { MatRadioGroupHarness } from '@angular/material/radio/testing';
import { MatSelectHarness } from '@angular/material/select/testing';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { provideDateFnsAdapter } from '@angular/material-date-fns-adapter';
import { pl } from 'date-fns/locale';
import { CATEGORY_LABELS, CaseFormValue, ProgressStep, ReducedPhoto } from '../../core/model';
import { CaseFormPage } from './case-form.page';
import { CaseSubmitter } from './case-submitter';
import texts from '../../shared/texts.pl.json';

registerLocaleData(localePl);

class FakeSubmitter extends CaseSubmitter {
  readonly progressStep = signal<ProgressStep | null>(null);
  readonly submitting = signal(false);
  readonly submitError = signal(false);
  readonly calls: { form: CaseFormValue; photo: ReducedPhoto }[] = [];
  submit(form: CaseFormValue, photo: ReducedPhoto): void {
    this.calls.push({ form, photo });
    this.submitError.set(false);
    this.submitting.set(true);
  }
}

class FakeOffscreenCanvas {
  constructor(
    public width: number,
    public height: number,
  ) {}
  getContext() {
    return { fillStyle: '', fillRect: () => undefined, drawImage: () => undefined };
  }
  convertToBlob(opts: { type: string }) {
    return Promise.resolve(new Blob(['jpeg'], { type: opts.type }));
  }
}

const CATEGORY_ORDER = [
  'Smartfon',
  'Laptop',
  'Tablet',
  'Komputer stacjonarny',
  'Monitor',
  'Telewizor',
  'Słuchawki / Audio',
  'Smartwatch / Opaska',
  'Inne',
];

describe('CaseFormPage', () => {
  let fixture: ComponentFixture<CaseFormPage>;
  let loader: HarnessLoader;
  let submitter: FakeSubmitter;
  let el: HTMLElement;

  async function setup(): Promise<void> {
    submitter = new FakeSubmitter();
    await TestBed.configureTestingModule({
      imports: [CaseFormPage],
      providers: [
        provideDateFnsAdapter(),
        { provide: MAT_DATE_LOCALE, useValue: pl },
        { provide: LOCALE_ID, useValue: 'pl' },
        { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { appearance: 'outline' } },
        { provide: CaseSubmitter, useValue: submitter },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CaseFormPage);
    el = fixture.nativeElement;
    loader = TestbedHarnessEnvironment.loader(fixture);
    await fixture.whenStable();
  }

  beforeEach(async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 10, 26, 12, 0, 0));
    vi.stubGlobal('OffscreenCanvas', FakeOffscreenCanvas);
    vi.stubGlobal(
      'createImageBitmap',
      vi.fn().mockResolvedValue({ width: 2000, height: 1000, close: () => undefined }),
    );
    vi.stubGlobal(
      'URL',
      Object.assign(URL, { createObjectURL: () => 'blob:fake', revokeObjectURL: () => undefined }),
    );
    await setup();
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  const field = (label: RegExp) =>
    loader.getHarness(MatFormFieldHarness.with({ floatingLabelText: label }));
  const submitButton = () =>
    loader.getHarness(MatButtonHarness.with({ text: texts['form.submit'] }));
  const dateInput = () => loader.getHarness(MatDatepickerInputHarness);
  const modelInput = () =>
    loader.getHarness(MatInputHarness.with({ placeholder: texts['form.model.placeholder'] }));
  const reasonInput = () => loader.getHarness(MatInputHarness.with({ selector: 'textarea' }));
  const select = () => loader.getHarness(MatSelectHarness);

  async function pickPhoto(name = 'phone-1.jpg', type = 'image/jpeg'): Promise<void> {
    const input = el.querySelector<HTMLInputElement>('input[type=file]')!;
    Object.defineProperty(input, 'files', {
      value: [new File(['x'], name, { type })],
      configurable: true,
    });
    input.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }

  async function fillValid(type: 'Reklamacja' | 'Zwrot' = 'Zwrot'): Promise<void> {
    const group = await loader.getHarness(MatRadioGroupHarness);
    await group.checkRadioButton({ label: new RegExp(`^${type}`) });
    await (await select()).clickOptions({ text: 'Smartfon' });
    await (await modelInput()).setValue('  Galaxy S23  ');
    await (await dateInput()).setValue('24.11.2026');
    await (
      await reasonInput()
    ).setValue(type === 'Reklamacja' ? '  Ekran przestał reagować na dotyk.  ' : '');
    await pickPhoto();
  }

  describe('layout (AC-01..AC-03)', () => {
    it('shows the info line near the top', () => {
      const first = el.querySelector('.case-form__info');
      expect(first?.textContent?.trim()).toBe(texts['form.info']);
      expect(el.querySelector('form')!.firstElementChild).toBe(first);
    });

    it('renders the fields in the AC-01 order', () => {
      const order = Array.from(
        el.querySelectorAll('app-case-type-cards, mat-form-field, app-photo-picker'),
      ).map((e) =>
        e.tagName === 'MAT-FORM-FIELD'
          ? (e.querySelector('mat-label')?.textContent ?? '').trim()
          : e.tagName.toLowerCase(),
      );
      expect(order).toEqual([
        'app-case-type-cards',
        expect.stringMatching(/Kategoria/),
        expect.stringMatching(/Nazwa lub model/),
        expect.stringMatching(/Data zakupu/),
        expect.stringMatching(/Powód/),
        'app-photo-picker',
      ]);
    });

    it('offers Reklamacja and Zwrot with nothing preselected', async () => {
      const group = await loader.getHarness(MatRadioGroupHarness);
      const radios = await group.getRadioButtons();
      expect(radios).toHaveLength(2);
      expect(await group.getCheckedValue()).toBeNull();
    });

    it('lists the nine categories in AC-03 order, none selected', async () => {
      const s = await select();
      expect(await s.getValueText()).toBe('');
      await s.open();
      const options = await s.getOptions();
      expect(await Promise.all(options.map((o) => o.getText()))).toEqual(CATEGORY_ORDER);
      expect(CATEGORY_ORDER).toEqual(Object.values(CATEGORY_LABELS));
    });

    it('has a model placeholder, and the submit button stays enabled while idle', async () => {
      expect(await (await modelInput()).getPlaceholder()).toBe(texts['form.model.placeholder']);
      expect(await (await submitButton()).isDisabled()).toBe(false);
    });
  });

  describe('reason (AC-06/07)', () => {
    const reasonFormField = () => el.querySelector('textarea')!.closest('mat-form-field')!;

    it('marks the reason as required only for Reklamacja', async () => {
      const group = await loader.getHarness(MatRadioGroupHarness);
      expect(await (await reasonInput()).isRequired()).toBe(false);
      await group.checkRadioButton({ label: /^Reklamacja/ });
      expect(await (await reasonInput()).isRequired()).toBe(true);
      const marker = () => reasonFormField().querySelector('.mat-mdc-form-field-required-marker');
      expect(marker()).toBeTruthy();
      await group.checkRadioButton({ label: /^Zwrot/ });
      expect(await (await reasonInput()).isRequired()).toBe(false);
      expect(marker()).toBeNull();
    });

    it('shows a live counter n/2000', async () => {
      const input = await reasonInput();
      const reasonField = await field(/Powód/);
      expect(await reasonField.getTextHints()).toEqual(['0/2000']);
      await input.setValue('abcde');
      expect(await reasonField.getTextHints()).toEqual(['5/2000']);
    });

    it('limits the reason to 2000 characters', async () => {
      expect(el.querySelector('textarea')!.getAttribute('maxlength')).toBe('2000');
    });

    it('rejects a Reklamacja reason shorter than 20 trimmed characters', async () => {
      const group = await loader.getHarness(MatRadioGroupHarness);
      await group.checkRadioButton({ label: /^Reklamacja/ });
      const input = await reasonInput();
      await input.setValue('   krótki opis      ');
      await input.blur();
      expect(await (await field(/Powód/)).getTextErrors()).toEqual([texts['form.reason.tooShort']]);
      await input.setValue('To jest wystarczająco długi opis.');
      expect(await (await field(/Powód/)).getTextErrors()).toEqual([]);
    });

    it('accepts an empty reason for Zwrot', async () => {
      const group = await loader.getHarness(MatRadioGroupHarness);
      await group.checkRadioButton({ label: /^Zwrot/ });
      const input = await reasonInput();
      await input.focus();
      await input.blur();
      expect(await (await field(/Powód/)).getTextErrors()).toEqual([]);
    });

    it('revalidates the reason when the type changes to Reklamacja', async () => {
      const group = await loader.getHarness(MatRadioGroupHarness);
      await group.checkRadioButton({ label: /^Zwrot/ });
      const input = await reasonInput();
      await input.setValue('krótko');
      await input.blur();
      expect(await (await field(/Powód/)).getTextErrors()).toEqual([]);
      await group.checkRadioButton({ label: /^Reklamacja/ });
      expect(await (await field(/Powód/)).getTextErrors()).toEqual([texts['form.reason.tooShort']]);
    });
  });

  describe('model (AC-04)', () => {
    it.each(['', ' a ', 'x'.repeat(101)])('rejects %j after blur', async (value) => {
      const input = await modelInput();
      await input.setValue(value);
      await input.blur();
      expect(await (await field(/Nazwa lub model/)).getTextErrors()).toEqual([
        texts['form.model.invalid'],
      ]);
    });

    it('accepts 2 and 100 characters', async () => {
      const input = await modelInput();
      for (const value of ['ab', 'x'.repeat(100)]) {
        await input.setValue(value);
        await input.blur();
        expect(await (await field(/Nazwa lub model/)).getTextErrors()).toEqual([]);
      }
    });
  });

  describe('purchase date (AC-05)', () => {
    async function errorsFor(value: string): Promise<string[]> {
      const input = await dateInput();
      await input.setValue(value);
      await input.blur();
      return (await field(/Data zakupu/)).getTextErrors();
    }

    it('requires a date', async () => {
      expect(await errorsFor('')).toEqual([texts['form.purchaseDate.required']]);
    });

    it.each(['24.11.2026', '2026-11-24', '4.9.2026', '26.11.2026', '26.11.2016'])(
      'accepts %s',
      async (value) => {
        expect(await errorsFor(value)).toEqual([]);
      },
    );

    it('shows the typed dd.MM.yyyy date back', async () => {
      const input = await dateInput();
      await input.setValue('2026-11-24');
      await input.blur();
      expect(await input.getValue()).toBe('24.11.2026');
    });

    it('rejects an impossible date', async () => {
      expect(await errorsFor('31.02.2026')).toEqual([texts['form.purchaseDate.invalid']]);
    });

    it('rejects text that is not a date', async () => {
      expect(await errorsFor('wczoraj')).toEqual([texts['form.purchaseDate.invalid']]);
    });

    it('rejects tomorrow', async () => {
      expect(await errorsFor('27.11.2026')).toEqual([texts['form.purchaseDate.future']]);
    });

    it('rejects a date more than 10 years ago', async () => {
      expect(await errorsFor('25.11.2016')).toEqual([texts['form.purchaseDate.tooOld']]);
    });
  });

  describe('invalid submit (AC-13)', () => {
    it('shows every error and focuses the first invalid control (case type)', async () => {
      await (await submitButton()).click();
      expect(submitter.calls).toHaveLength(0);
      const text = el.textContent ?? '';
      for (const key of [
        'form.caseType.required',
        'form.category.required',
        'form.model.invalid',
        'form.purchaseDate.required',
        'form.photo.required',
      ] as const) {
        expect(text).toContain(texts[key]);
      }
      expect(document.activeElement?.getAttribute('type')).toBe('radio');
      expect(el.contains(document.activeElement)).toBe(true);
    });

    it('focuses the model input when only it and later fields are invalid', async () => {
      const group = await loader.getHarness(MatRadioGroupHarness);
      await group.checkRadioButton({ label: /^Zwrot/ });
      await (await select()).clickOptions({ text: 'Laptop' });
      await (await submitButton()).click();
      expect(document.activeElement).toBe(
        el.querySelector('input[placeholder="np. Galaxy S23, ThinkPad T14"]'),
      );
    });

    it('focuses the photo button when only the photo is missing', async () => {
      await fillValid('Zwrot');
      el.querySelector('[data-testid="photo-thumbnail"]');
      const remove = await loader.getHarness(
        MatButtonHarness.with({ text: texts['form.photo.remove'] }),
      );
      await remove.click();
      await (await submitButton()).click();
      expect(document.activeElement?.textContent?.trim()).toBe(texts['form.photo.button']);
      expect(el.textContent).toContain(texts['form.photo.required']);
    });

    it('does not show errors before blur or submit', async () => {
      expect(el.textContent).not.toContain(texts['form.model.invalid']);
      expect(el.textContent).not.toContain(texts['form.photo.required']);
      expect(el.textContent).not.toContain(texts['form.caseType.required']);
    });
  });

  describe('valid submit (AC-14)', () => {
    it('sends trimmed values and the reduced photo to the submitter', async () => {
      await fillValid('Reklamacja');
      await (await submitButton()).click();
      expect(submitter.calls).toHaveLength(1);
      const { form, photo } = submitter.calls[0];
      expect(form.caseType).toBe('COMPLAINT');
      expect(form.category).toBe('SMARTPHONE');
      expect(form.model).toBe('Galaxy S23');
      expect(form.reason).toBe('Ekran przestał reagować na dotyk.');
      expect(form.purchaseDate.getFullYear()).toBe(2026);
      expect(form.purchaseDate.getMonth()).toBe(10);
      expect(form.purchaseDate.getDate()).toBe(24);
      expect(form.photo.name).toBe('phone-1.jpg');
      expect(photo.originalName).toBe('phone-1.jpg');
      expect(photo.width).toBe(1568);
    });

    it('disables the inputs and shows the three progress steps', async () => {
      await fillValid('Zwrot');
      await (await submitButton()).click();
      await fixture.whenStable();
      expect(await (await modelInput()).isDisabled()).toBe(true);
      expect(await (await select()).isDisabled()).toBe(true);
      expect(await (await submitButton()).isDisabled()).toBe(true);
      const steps = Array.from(el.querySelectorAll('app-submit-progress li')).map((li) =>
        li.textContent?.trim(),
      );
      expect(steps).toEqual([
        texts['progress.UPLOAD_RECEIVED'],
        texts['progress.ANALYZING_PHOTO'],
        texts['progress.PREPARING_DECISION'],
      ]);
    });

    it('highlights the step reported by the submitter', async () => {
      await fillValid('Zwrot');
      await (await submitButton()).click();
      submitter.progressStep.set('ANALYZING_PHOTO');
      await fixture.whenStable();
      const current = el.querySelector('app-submit-progress li[aria-current="step"]');
      expect(current?.textContent?.trim()).toBe(texts['progress.ANALYZING_PHOTO']);
    });

    it('does not show progress before submitting', () => {
      expect(el.querySelector('app-submit-progress')).toBeNull();
    });
  });

  describe('submit error banner (AC-40)', () => {
    async function failFirstSubmit(): Promise<void> {
      await fillValid('Zwrot');
      await (await submitButton()).click();
      submitter.submitting.set(false);
      submitter.submitError.set(true);
      await fixture.whenStable();
    }

    it('shows the banner with retry, re-enables the form and keeps the data', async () => {
      await failFirstSubmit();
      expect(el.textContent).toContain(texts['form.submitError']);
      expect(await (await modelInput()).getValue()).toBe('  Galaxy S23  ');
      expect(await (await modelInput()).isDisabled()).toBe(false);
      expect(el.querySelector('[data-testid="photo-thumbnail"]')).toBeTruthy();
      expect(el.textContent).toContain('phone-1.jpg');
      await loader.getHarness(MatButtonHarness.with({ text: texts['common.retry'] }));
    });

    it('re-submits the same stored values and photo on retry', async () => {
      await failFirstSubmit();
      const retry = await loader.getHarness(MatButtonHarness.with({ text: texts['common.retry'] }));
      await retry.click();
      expect(submitter.calls).toHaveLength(2);
      expect(submitter.calls[1].form).toEqual(submitter.calls[0].form);
      expect(submitter.calls[1].photo).toBe(submitter.calls[0].photo);
    });

    it('has no banner when there is no error', () => {
      expect(el.textContent).not.toContain(texts['form.submitError']);
    });
  });
});
