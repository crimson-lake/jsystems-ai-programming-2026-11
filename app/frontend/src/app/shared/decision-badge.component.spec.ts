import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DecisionLabel } from '../core/model';
import { DecisionBadgeComponent } from './decision-badge.component';
import texts from './texts.pl.json';

@Component({
  imports: [DecisionBadgeComponent],
  template: `<app-decision-badge [label]="label" />`,
})
class Host {
  label: DecisionLabel = 'ACCEPTED_PRELIMINARY';
}

describe('DecisionBadgeComponent', () => {
  const cases: [DecisionLabel, string, string][] = [
    ['ACCEPTED_PRELIMINARY', texts['label.ACCEPTED_PRELIMINARY'], 'accepted'],
    ['REJECTED', texts['label.REJECTED'], 'rejected'],
    ['NEEDS_MORE_INFO', texts['label.NEEDS_MORE_INFO'], 'needs-info'],
    ['ESCALATED', texts['label.ESCALATED'], 'escalated'],
  ];

  it.each(cases)('%s shows the Polish text and the --app-badge-%s colour class', async (label, text, modifier) => {
    TestBed.configureTestingModule({ imports: [Host] });
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.label = label;
    await fixture.whenStable();
    const badge = (fixture.nativeElement as HTMLElement).querySelector('.badge')!;
    expect(badge.textContent?.trim()).toBe(text);
    expect(badge.classList.contains(`badge--${modifier}`)).toBe(true);
  });
});
