import { Injectable, Provider } from '@angular/core';
import { MAT_DATE_FORMATS, MatDateFormats } from '@angular/material/core';
import { MatDatepickerIntl } from '@angular/material/datepicker';
import { DATEPICKER_LABELS } from './case-form.labels';

/** Typed input formats of the purchase date (ADR-003 section 3.4); the first one is displayed. */
export const PURCHASE_DATE_PARSE_FORMATS = ['dd.MM.yyyy', 'd.M.yyyy', 'yyyy-MM-dd'];

export const CASE_FORM_DATE_FORMATS: MatDateFormats = {
  parse: { dateInput: PURCHASE_DATE_PARSE_FORMATS },
  display: {
    dateInput: 'dd.MM.yyyy',
    monthYearLabel: 'LLLL yyyy',
    dateA11yLabel: 'd MMMM yyyy',
    monthYearA11yLabel: 'LLLL yyyy',
  },
};

@Injectable()
export class PolishDatepickerIntl extends MatDatepickerIntl {
  constructor() {
    super();
    Object.assign(this, DATEPICKER_LABELS);
  }
}

/** Component-level providers of the case form; the global date adapter stays in app.config.ts. */
export const CASE_FORM_DATE_PROVIDERS: Provider[] = [
  { provide: MAT_DATE_FORMATS, useValue: CASE_FORM_DATE_FORMATS },
  { provide: MatDatepickerIntl, useClass: PolishDatepickerIntl },
];
