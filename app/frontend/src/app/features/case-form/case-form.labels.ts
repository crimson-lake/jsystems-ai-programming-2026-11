import { t } from '../../shared/texts.pl';

/** Field labels of the case form; the texts live in shared/texts.pl.json (single source). */
export const FORM_LABELS = {
  caseType: t('form.label.caseType'),
  category: t('form.label.category'),
  model: t('form.label.model'),
  purchaseDate: t('form.label.purchaseDate'),
  purchaseDatePlaceholder: t('form.purchaseDate.placeholder'),
  reason: t('form.label.reason'),
  photo: t('form.label.photo'),
  progressTitle: t('progress.title'),
} as const;

/** Polish labels of the date picker chrome (Material defaults are English). */
export const DATEPICKER_LABELS = {
  calendarLabel: t('datepicker.calendar'),
  openCalendarLabel: t('datepicker.open'),
  closeCalendarLabel: t('datepicker.close'),
  prevMonthLabel: t('datepicker.prevMonth'),
  nextMonthLabel: t('datepicker.nextMonth'),
  prevYearLabel: t('datepicker.prevYear'),
  nextYearLabel: t('datepicker.nextYear'),
  prevMultiYearLabel: t('datepicker.prevMultiYear'),
  nextMultiYearLabel: t('datepicker.nextMultiYear'),
  switchToMonthViewLabel: t('datepicker.toMonthView'),
  switchToMultiYearViewLabel: t('datepicker.toMultiYearView'),
} as const;
