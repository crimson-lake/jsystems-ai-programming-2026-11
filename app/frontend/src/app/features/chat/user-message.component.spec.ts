import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatDialogHarness } from '@angular/material/dialog/testing';
import { FakeCaseStore, uiMessage } from '../../../testing/fake-case-store';
import { UiMessage } from '../../core/model';
import { CaseStore } from '../../core/state/case.store';
import { NewCaseService } from './new-case.service';
import { UserMessageComponent } from './user-message.component';
import texts from '../../shared/texts.pl.json';

describe('UserMessageComponent', () => {
  let store: FakeCaseStore;
  let startCount: number;
  let fixture: ComponentFixture<UserMessageComponent>;
  let loader: HarnessLoader;
  let el: HTMLElement;

  async function render(overrides: Partial<UiMessage> = {}): Promise<void> {
    fixture = TestBed.createComponent(UserMessageComponent);
    fixture.componentRef.setInput(
      'message',
      uiMessage({ id: 'u1', role: 'user', text: 'Jaką datę zakupu podałem?', isFirst: false, ...overrides }),
    );
    el = fixture.nativeElement;
    loader = TestbedHarnessEnvironment.documentRootLoader(fixture);
    await fixture.whenStable();
  }

  beforeEach(() => {
    store = new FakeCaseStore();
    startCount = 0;
    TestBed.configureTestingModule({
      providers: [
        { provide: CaseStore, useValue: store },
        { provide: NewCaseService, useValue: { start: () => startCount++ } },
      ],
    });
  });

  it('shows the text (line breaks kept) and the time', async () => {
    await render({ text: 'Linia 1\nLinia 2', createdAt: new Date(2026, 10, 26, 9, 7) });
    expect(el.querySelector('.user__text')?.textContent).toBe('Linia 1\nLinia 2');
    expect(el.querySelector('time')?.textContent?.trim()).toBe('09:07');
  });

  it('shows no thumbnail without a photo and a thumbnail with one', async () => {
    await render();
    expect(el.querySelector('img')).toBeNull();
    await render({ photoUrl: 'blob:chat-photo' });
    expect(el.querySelector('img')?.getAttribute('src')).toBe('blob:chat-photo');
  });

  it('opens the enlarged photo on thumbnail click', async () => {
    await render({ photoUrl: 'blob:chat-photo' });
    el.querySelector<HTMLButtonElement>('.user__photo')!.click();
    const dialog = await loader.getHarness(MatDialogHarness);
    expect(await dialog.host()).toBeTruthy();
    expect(document.querySelector('mat-dialog-container img')?.getAttribute('src')).toBe('blob:chat-photo');
  });

  it('shows no error and no retry for a message that is not failed', async () => {
    await render({ status: 'complete' });
    expect(el.querySelector('[role="alert"]')).toBeNull();
    expect(el.querySelector('button')).toBeNull();
  });

  it('failed: shows the inline error from the store and "Spróbuj ponownie", which retries (AC-41)', async () => {
    store.messageErrors.set({ u1: 'chat.error.generic' });
    await render({ status: 'failed' });
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(texts['chat.error.generic']);
    const retry = await loader.getHarness(MatButtonHarness.with({ text: texts['common.retry'] }));
    await retry.click();
    expect(store.retried).toEqual(['u1']);
  });

  it('failed with CASE_BUSY shows the busy text', async () => {
    store.messageErrors.set({ u1: 'chat.error.busy' });
    await render({ status: 'failed' });
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(texts['chat.error.busy']);
  });

  it('failed without a stored key falls back to the generic error', async () => {
    await render({ status: 'failed' });
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(texts['chat.error.generic']);
  });

  it('expired case: shows "Nowa sprawa" that starts a new case without the dialog, and no retry', async () => {
    store.messageErrors.set({ u1: 'chat.error.expired' });
    await render({ status: 'failed' });
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(texts['chat.error.expired']);
    const buttons = await loader.getAllHarnesses(MatButtonHarness);
    expect(await Promise.all(buttons.map((b) => b.getText()))).toEqual([texts['common.newCase']]);
    await buttons[0].click();
    expect(startCount).toBe(1);
    expect(document.querySelector('mat-dialog-container')).toBeNull();
  });
});
