import { Component, inject } from '@angular/core';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatDialogHarness } from '@angular/material/dialog/testing';
import { NewCaseDialogComponent } from './new-case-dialog.component';
import texts from '../../shared/texts.pl.json';

@Component({ template: '' })
class Host {
  readonly dialog = inject(MatDialog);
}

describe('NewCaseDialogComponent (PRD 9.3, AC-44)', () => {
  let fixture: ComponentFixture<Host>;
  let loader: HarnessLoader;
  let result: Promise<boolean | undefined>;

  beforeEach(async () => {
    // jsdom has no layout, so the CDK focus trap would treat every element as hidden and skip
    // `cdkFocusInitial`; report a size so it behaves like in a browser.
    Object.defineProperty(HTMLElement.prototype, 'offsetWidth', { configurable: true, get: () => 1 });
    TestBed.configureTestingModule({ imports: [Host] });
    fixture = TestBed.createComponent(Host);
    loader = TestbedHarnessEnvironment.documentRootLoader(fixture);
    result = new Promise((resolve) => {
      fixture.componentInstance.dialog
        .open<NewCaseDialogComponent, void, boolean>(NewCaseDialogComponent)
        .afterClosed()
        .subscribe(resolve);
    });
    await fixture.whenStable();
  });

  afterEach(() => {
    Reflect.deleteProperty(HTMLElement.prototype, 'offsetWidth');
  });

  it('shows the exact PRD text', async () => {
    const dialog = await loader.getHarness(MatDialogHarness);
    expect(await dialog.getText()).toContain(texts['dialog.newCase.text']);
  });

  it('has "Anuluj" and "Nowa sprawa" buttons with the initial focus on "Anuluj"', async () => {
    const cancel = await loader.getHarness(MatButtonHarness.with({ text: texts['dialog.newCase.cancel'] }));
    await loader.getHarness(MatButtonHarness.with({ text: texts['dialog.newCase.confirm'] }));
    // The focus trap moves focus after the dialog has rendered.
    await vi.waitFor(async () => expect(await cancel.isFocused()).toBe(true));
  });

  it('closes with true on "Nowa sprawa"', async () => {
    const confirm = await loader.getHarness(MatButtonHarness.with({ text: texts['dialog.newCase.confirm'] }));
    await confirm.click();
    expect(await result).toBe(true);
  });

  it('closes with false on "Anuluj"', async () => {
    const cancel = await loader.getHarness(MatButtonHarness.with({ text: texts['dialog.newCase.cancel'] }));
    await cancel.click();
    expect(await result).toBe(false);
  });

  it('Escape is a cancel (no confirmation)', async () => {
    const dialog = await loader.getHarness(MatDialogHarness);
    await dialog.close();
    expect(await result).not.toBe(true);
  });
});
