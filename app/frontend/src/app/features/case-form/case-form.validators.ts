import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { startOfDay, subYears } from 'date-fns';

export const REASON_MIN_COMPLAINT = 20;
export const REASON_MAX = 2000;

/** Trimmed length 2..100 (AC-04); empty counts as invalid with the same text. */
export function modelValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const length = String(control.value ?? '').trim().length;
    return length >= 2 && length <= 100 ? null : { model: true };
  };
}

/** Reason needs >= 20 trimmed characters for a complaint only (AC-06/07). */
export function reasonValidator(caseTypeOf: () => string | null): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    if (caseTypeOf() !== 'COMPLAINT') return null;
    return String(control.value ?? '').trim().length >= REASON_MIN_COMPLAINT
      ? null
      : { tooShort: true };
  };
}

/** `future` after today, `tooOld` before today minus 10 years (AC-05). Null and Invalid Date are left to other validators. */
export function purchaseDateValidator(today: () => Date): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;
    if (!(value instanceof Date) || Number.isNaN(value.getTime())) return null;
    const day = startOfDay(value);
    const now = startOfDay(today());
    if (day > now) return { future: true };
    if (day < subYears(now, 10)) return { tooOld: true };
    return null;
  };
}
