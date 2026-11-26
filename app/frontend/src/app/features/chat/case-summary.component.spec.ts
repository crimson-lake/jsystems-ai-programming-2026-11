import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatDialogHarness } from '@angular/material/dialog/testing';
import { FakeCaseStore, decisionView } from '../../../testing/fake-case-store';
import { CaseFormValue, ReducedPhoto } from '../../core/model';
import { CaseStore } from '../../core/state/case.store';
import { CaseSummaryComponent } from './case-summary.component';
import { NewCaseService } from './new-case.service';
import texts from '../../shared/texts.pl.json';

function formValue(overrides: Partial<CaseFormValue> = {}): CaseFormValue {
  return {
    caseType: 'RETURN',
    category: 'SMARTPHONE',
    model: 'Galaxy S23',
    purchaseDate: new Date(2026, 10, 6),
    reason: '',
    photo: new File(['x'], 'phone-1.jpg', { type: 'image/jpeg' }),
    ...overrides,
  };
}

const PHOTO: ReducedPhoto = {
  blob: new Blob(['x'], { type: 'image/jpeg' }),
  objectUrl: 'blob:summary-photo',
  originalName: 'phone-1.jpg',
  width: 10,
  height: 10,
};

describe('CaseSummaryComponent (PRD 9.2 summary panel)', () => {
  let store: FakeCaseStore;
  let confirmCount: number;
  let fixture: ComponentFixture<CaseSummaryComponent>;
  let loader: HarnessLoader;
  let el: HTMLElement;

  beforeEach(async () => {
    store = new FakeCaseStore();
    store.form.set(formValue());
    store.formPhoto.set(PHOTO);
    store.decision.set(decisionView({ label: 'REJECTED' }));
    confirmCount = 0;
    TestBed.configureTestingModule({
      providers: [
        { provide: CaseStore, useValue: store },
        { provide: NewCaseService, useValue: { confirmAndStart: () => confirmCount++ } },
      ],
    });
    fixture = TestBed.createComponent(CaseSummaryComponent);
    el = fixture.nativeElement;
    loader = TestbedHarnessEnvironment.documentRootLoader(fixture);
    await fixture.whenStable();
  });

  const rows = () =>
    Array.from(el.querySelectorAll('.summary__row')).map((r) => [
      r.querySelector('dt')?.textContent?.trim(),
      r.querySelector('dd')?.textContent?.trim(),
    ]);

  it('exposes the summary container', () => {
    expect(el.querySelector('[data-testid=case-summary]')).toBeTruthy();
  });

  it('shows type, category, model and the Polish purchase date with form labels', () => {
    expect(rows()).toEqual([
      [texts['form.label.caseType'], texts['form.caseType.return']],
      [texts['form.label.category'], 'Smartfon'],
      [texts['form.label.model'], 'Galaxy S23'],
      [texts['form.label.purchaseDate'], '06.11.2026'],
    ]);
  });

  it('shows "Reklamacja" for a complaint', async () => {
    store.form.set(formValue({ caseType: 'COMPLAINT', category: 'LAPTOP' }));
    await fixture.whenStable();
    expect(rows()[0][1]).toBe(texts['form.caseType.complaint']);
    expect(rows()[1][1]).toBe('Laptop');
  });

  it('shows the thumbnail from the store photo', () => {
    const img = el.querySelector<HTMLImageElement>('img[data-testid=summary-thumbnail]')!;
    expect(img.getAttribute('src')).toBe('blob:summary-photo');
    expect(img.getAttribute('alt')).toBe(texts['form.label.photo']);
  });

  it('shows the current decision badge and follows changes (AC-35/36)', async () => {
    const badge = () => el.querySelector('[data-testid=summary-decision-badge]')?.textContent?.trim();
    expect(badge()).toBe(texts['label.REJECTED']);
    store.decision.set(decisionView({ label: 'ESCALATED' }));
    await fixture.whenStable();
    expect(badge()).toBe(texts['label.ESCALATED']);
  });

  it('shows no badge while there is no decision', async () => {
    store.decision.set(null);
    await fixture.whenStable();
    expect(el.querySelector('[data-testid=summary-decision-badge]')).toBeNull();
  });

  it('opens an enlarged photo dialog on thumbnail click', async () => {
    el.querySelector<HTMLButtonElement>('.summary__photo')!.click();
    await loader.getHarness(MatDialogHarness);
    expect(document.querySelector('mat-dialog-container img')?.getAttribute('src')).toBe('blob:summary-photo');
  });

  it('"Nowa sprawa" asks the new-case flow for confirmation', async () => {
    const button = await loader.getHarness(MatButtonHarness.with({ text: texts['common.newCase'] }));
    await button.click();
    expect(confirmCount).toBe(1);
  });

  it('renders an optional heading only when asked', async () => {
    expect(el.querySelector('h2')).toBeNull();
    fixture.componentRef.setInput('showTitle', true);
    await fixture.whenStable();
    expect(el.querySelector('h2')?.textContent?.trim()).toBe(texts['chat.summaryTitle']);
  });
});
