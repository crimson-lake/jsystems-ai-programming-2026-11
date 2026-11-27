import { Component } from '@angular/core';
import { FormControl } from '@angular/forms';
import { TestBed } from '@angular/core/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatRadioGroupHarness } from '@angular/material/radio/testing';
import { CaseType } from '../../core/model';
import { CaseTypeCardsComponent } from './case-type-cards.component';
import texts from '../../shared/texts.pl.json';

@Component({
  imports: [CaseTypeCardsComponent],
  template: `<app-case-type-cards [control]="control" />`,
})
class Host {
  readonly control = new FormControl<CaseType | null>(null);
}

describe('CaseTypeCardsComponent', () => {
  let loader: HarnessLoader;
  let host: Host;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Host] }).compileComponents();
    const fixture = TestBed.createComponent(Host);
    host = fixture.componentInstance;
    loader = TestbedHarnessEnvironment.loader(fixture);
    await fixture.whenStable();
  });

  it('renders Reklamacja then Zwrot with descriptions and nothing selected', async () => {
    const group = await loader.getHarness(MatRadioGroupHarness);
    const radios = await group.getRadioButtons();
    expect(await Promise.all(radios.map((r) => r.getLabelText()))).toEqual([
      `${texts['form.caseType.complaint']}${texts['form.caseType.complaintDesc']}`,
      `${texts['form.caseType.return']}${texts['form.caseType.returnDesc']}`,
    ]);
    expect(await group.getCheckedValue()).toBeNull();
  });

  it('writes COMPLAINT / RETURN to the control', async () => {
    const group = await loader.getHarness(MatRadioGroupHarness);
    const [complaint, ret] = await group.getRadioButtons();
    await ret.check();
    expect(host.control.value).toBe('RETURN');
    await complaint.check();
    expect(host.control.value).toBe('COMPLAINT');
  });
});
