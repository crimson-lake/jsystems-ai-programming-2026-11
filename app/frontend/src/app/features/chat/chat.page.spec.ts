import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatExpansionPanelHarness } from '@angular/material/expansion/testing';
import { BehaviorSubject } from 'rxjs';
import { FakeCaseStore, decisionView, uiMessage } from '../../../testing/fake-case-store';
import { CaseFormValue, ReducedPhoto } from '../../core/model';
import { CaseStore } from '../../core/state/case.store';
import { ChatPage } from './chat.page';
import { NewCaseService } from './new-case.service';
import texts from '../../shared/texts.pl.json';

describe('ChatPage layout (ADR-003 3.3)', () => {
  let store: FakeCaseStore;
  let wide: BehaviorSubject<BreakpointState>;
  let fixture: ComponentFixture<ChatPage>;
  let loader: HarnessLoader;
  let el: HTMLElement;

  async function render(matches: boolean): Promise<void> {
    wide = new BehaviorSubject<BreakpointState>({ matches, breakpoints: {} });
    TestBed.configureTestingModule({
      providers: [
        { provide: CaseStore, useValue: store },
        { provide: NewCaseService, useValue: { start: () => undefined, confirmAndStart: () => undefined } },
        { provide: BreakpointObserver, useValue: { observe: () => wide } },
      ],
    });
    fixture = TestBed.createComponent(ChatPage);
    el = fixture.nativeElement;
    loader = TestbedHarnessEnvironment.loader(fixture);
    await fixture.whenStable();
  }

  beforeEach(() => {
    store = new FakeCaseStore();
    store.form.set({
      caseType: 'RETURN',
      category: 'SMARTPHONE',
      model: 'Galaxy S23',
      purchaseDate: new Date(2026, 10, 6),
      reason: '',
      photo: new File(['x'], 'p.jpg'),
    } satisfies CaseFormValue);
    store.formPhoto.set({ blob: new Blob(['x']), objectUrl: 'blob:p', originalName: 'p.jpg', width: 1, height: 1 } satisfies ReducedPhoto);
    store.decision.set(decisionView());
    store.messages.set([uiMessage({ text: 'Odpowiedź', decision: decisionView() })]);
  });

  describe('>= 960 px', () => {
    beforeEach(() => render(true));

    it('puts the summary in a side mat-sidenav next to the chat', () => {
      const sidenav = el.querySelector('mat-sidenav')!;
      expect(sidenav.getAttribute('mode')).toBe('side');
      expect(sidenav.querySelector('[data-testid=case-summary]')).toBeTruthy();
      expect(el.querySelector('mat-sidenav-content app-message-list')).toBeTruthy();
      expect(el.querySelector('mat-expansion-panel')).toBeNull();
    });

    it('renders exactly one summary and one message list', () => {
      expect(el.querySelectorAll('[data-testid=case-summary]').length).toBe(1);
      expect(el.querySelectorAll('app-message-list').length).toBe(1);
    });

    it('reserves a slot for the composer below the list (T34)', () => {
      expect(el.querySelector('.chat__composer-slot')).toBeTruthy();
    });
  });

  describe('< 960 px', () => {
    beforeEach(() => render(false));

    it('uses a collapsed expansion panel "Szczegóły sprawy" above the chat', async () => {
      expect(el.querySelector('mat-sidenav')).toBeNull();
      const panel = await loader.getHarness(MatExpansionPanelHarness);
      expect(await panel.getTitle()).toBe(texts['chat.summaryTitle']);
      expect(await panel.isExpanded()).toBe(false);
      const summary = el.querySelector('[data-testid=case-summary]');
      const list = el.querySelector('app-message-list');
      expect(summary).toBeTruthy();
      expect(
        el.querySelector('mat-expansion-panel')!.compareDocumentPosition(list!) & Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
    });

    it('the header is a button named "Szczegóły sprawy" that opens the panel', async () => {
      const header = el.querySelector<HTMLElement>('mat-expansion-panel-header')!;
      expect(header.getAttribute('role')).toBe('button');
      expect(header.textContent?.trim()).toBe(texts['chat.summaryTitle']);
      const panel = await loader.getHarness(MatExpansionPanelHarness);
      await panel.expand();
      expect(await panel.isExpanded()).toBe(true);
    });
  });

  it('switches layout when the viewport crosses 960 px', async () => {
    await render(true);
    expect(el.querySelector('mat-sidenav')).toBeTruthy();
    wide.next({ matches: false, breakpoints: {} });
    await fixture.whenStable();
    expect(el.querySelector('mat-sidenav')).toBeNull();
    expect(el.querySelector('mat-expansion-panel')).toBeTruthy();
  });
});
