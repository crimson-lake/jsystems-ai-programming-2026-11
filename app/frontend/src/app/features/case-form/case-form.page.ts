import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelect, MatSelectModule } from '@angular/material/select';
import { startOfDay, subYears } from 'date-fns';
import {
  CATEGORY_CODES,
  CATEGORY_LABELS,
  CaseFormValue,
  CaseType,
  CategoryCode,
  ReducedPhoto,
} from '../../core/model';
import { t } from '../../shared/texts.pl';
import { FORM_LABELS } from './case-form.labels';
import { CASE_FORM_DATE_PROVIDERS } from './case-form.providers';
import {
  REASON_MAX,
  modelValidator,
  purchaseDateValidator,
  reasonValidator,
} from './case-form.validators';
import { CaseSubmitter } from './case-submitter';
import { CaseTypeCardsComponent } from './case-type-cards.component';
import { PhotoPickerComponent, PickedPhoto } from './photo-picker.component';
import { SubmitProgressComponent } from './submit-progress.component';

type FieldName = 'caseType' | 'category' | 'model' | 'purchaseDate' | 'reason' | 'photo';

/** Screen 1: the case form (PRD 9.1, ADR-003 3.4). */
@Component({
  selector: 'app-case-form-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    CaseTypeCardsComponent,
    PhotoPickerComponent,
    SubmitProgressComponent,
  ],
  providers: [CASE_FORM_DATE_PROVIDERS],
  templateUrl: './case-form.page.html',
  styleUrl: './case-form.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CaseFormPage {
  protected readonly submitter = inject(CaseSubmitter);

  protected readonly labels = FORM_LABELS;
  protected readonly infoText = t('form.info');
  protected readonly modelPlaceholder = t('form.model.placeholder');
  protected readonly submitText = t('form.submit');
  protected readonly submitErrorText = t('form.submitError');
  protected readonly retryText = t('common.retry');
  protected readonly maxReason = REASON_MAX;
  protected readonly categories = CATEGORY_CODES.map((code) => ({
    code,
    label: CATEGORY_LABELS[code],
  }));

  protected readonly today = startOfDay(new Date());
  protected readonly minDate = subYears(this.today, 10);

  private readonly caseTypeControl_ = new FormControl<CaseType | null>(null, Validators.required);

  readonly form = new FormGroup({
    caseType: this.caseTypeControl_,
    category: new FormControl<CategoryCode | null>(null, Validators.required),
    model: new FormControl('', { nonNullable: true, validators: [modelValidator()] }),
    purchaseDate: new FormControl<Date | null>(null, [
      Validators.required,
      purchaseDateValidator(() => this.today),
    ]),
    reason: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.maxLength(REASON_MAX),
        reasonValidator(() => this.caseTypeControl_.value),
      ],
    }),
    photo: new FormControl<PickedPhoto | null>(null, Validators.required),
  });

  /** Re-renders error texts on any form event (touched, status, value); forms are not signals. */
  private readonly formEvents = toSignal(this.form.events);
  private readonly caseType = toSignal(this.form.controls.caseType.valueChanges, {
    initialValue: null,
  });
  private readonly reasonValue = toSignal(this.form.controls.reason.valueChanges, {
    initialValue: '',
  });

  protected readonly isComplaint = computed(() => this.caseType() === 'COMPLAINT');
  protected readonly reasonLength = computed(() => this.reasonValue().length);
  protected readonly busy = this.submitter.submitting;
  private readonly submitted = signal(false);
  protected readonly photoRequired = computed(() => {
    this.formEvents();
    const photo = this.form.controls.photo;
    return photo.invalid && (photo.touched || this.submitted());
  });

  private readonly cards = viewChild(CaseTypeCardsComponent);
  private readonly categorySelect = viewChild(MatSelect);
  private readonly modelInput = viewChild('modelInput', { read: ElementRef<HTMLInputElement> });
  private readonly dateInput = viewChild('dateInput', { read: ElementRef<HTMLInputElement> });
  private readonly reasonInput = viewChild('reasonInput', {
    read: ElementRef<HTMLTextAreaElement>,
  });
  private readonly picker = viewChild(PhotoPickerComponent);

  private lastSubmission: { value: CaseFormValue; photo: ReducedPhoto } | null = null;

  constructor() {
    this.form.controls.caseType.valueChanges.subscribe(() =>
      this.form.controls.reason.updateValueAndValidity(),
    );
    effect(() => {
      if (this.busy()) this.form.disable({ emitEvent: false });
      else this.form.enable({ emitEvent: false });
    });
  }

  protected onPhotoChange(photo: PickedPhoto | null): void {
    this.form.controls.photo.setValue(photo);
    this.form.controls.photo.markAsTouched();
  }

  protected onSubmit(): void {
    if (this.busy()) return;
    this.submitted.set(true);
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.focusFirstInvalid();
      return;
    }
    const v = this.form.getRawValue();
    const picked = v.photo!;
    const value: CaseFormValue = {
      caseType: v.caseType!,
      category: v.category!,
      model: v.model.trim(),
      purchaseDate: v.purchaseDate!,
      reason: v.reason.trim(),
      photo: picked.file,
    };
    this.lastSubmission = { value, photo: picked.reduced };
    this.submitter.submit(value, picked.reduced);
  }

  protected retry(): void {
    if (this.busy() || !this.lastSubmission) return;
    this.submitter.submit(this.lastSubmission.value, this.lastSubmission.photo);
  }

  /** Error text of a field once Material shows it (touched or submitted); null when valid. */
  protected errorText(name: Exclude<FieldName, 'photo'>): string | null {
    this.formEvents();
    const errors = this.form.controls[name].errors;
    if (!errors) return null;
    switch (name) {
      case 'caseType':
        return t('form.caseType.required');
      case 'category':
        return t('form.category.required');
      case 'model':
        return t('form.model.invalid');
      case 'purchaseDate':
        if (errors['matDatepickerParse']) return t('form.purchaseDate.invalid');
        if (errors['future'] || errors['matDatepickerMax']) return t('form.purchaseDate.future');
        if (errors['tooOld'] || errors['matDatepickerMin']) return t('form.purchaseDate.tooOld');
        return t('form.purchaseDate.required');
      case 'reason':
        return errors['maxlength'] ? t('form.reason.tooLong') : t('form.reason.tooShort');
    }
  }

  protected showError(name: 'caseType'): boolean {
    this.formEvents();
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.submitted());
  }

  protected get caseTypeControl(): FormControl<CaseType | null> {
    return this.form.controls.caseType;
  }

  protected get photoType(): CaseType | null {
    return this.caseType();
  }

  private focusFirstInvalid(): void {
    const c = this.form.controls;
    const order: [FieldName, () => void][] = [
      ['caseType', () => this.cards()?.focus()],
      ['category', () => this.categorySelect()?.focus()],
      ['model', () => this.modelInput()?.nativeElement.focus()],
      ['purchaseDate', () => this.dateInput()?.nativeElement.focus()],
      ['reason', () => this.reasonInput()?.nativeElement.focus()],
      ['photo', () => this.picker()?.focus()],
    ];
    order.find(([name]) => c[name].invalid)?.[1]();
  }
}
