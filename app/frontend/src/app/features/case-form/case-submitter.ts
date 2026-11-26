import { Injectable, Signal, signal } from '@angular/core';
import { CaseFormValue, ProgressStep, ReducedPhoto } from '../../core/model';

/**
 * Seam between the case form and the case store (T32 provides the real implementation).
 * The store must set `submitting` synchronously inside `submit`, clear `submitError` when a new
 * submit starts and set it when the first submission fails (AC-40).
 */
@Injectable({ providedIn: 'root', useFactory: () => new NoopCaseSubmitter() })
export abstract class CaseSubmitter {
  abstract readonly progressStep: Signal<ProgressStep | null>;
  abstract readonly submitting: Signal<boolean>;
  abstract readonly submitError: Signal<boolean>;
  abstract submit(form: CaseFormValue, photo: ReducedPhoto): void;
}

/** Default provider until T32: does nothing. */
export class NoopCaseSubmitter extends CaseSubmitter {
  readonly progressStep = signal<ProgressStep | null>(null).asReadonly();
  readonly submitting = signal(false).asReadonly();
  readonly submitError = signal(false).asReadonly();
  submit(): void {
    // intentionally empty
  }
}
