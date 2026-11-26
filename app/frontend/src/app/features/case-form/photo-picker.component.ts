import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnInit,
  computed,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { CaseType, ReducedPhoto } from '../../core/model';
import { PhotoReduceError, reducePhoto } from '../../core/image/photo-reducer';
import { t } from '../../shared/texts.pl';
import { FORM_LABELS } from './case-form.labels';

/** A valid pick: the original file (form value) and its reduced JPEG (upload). */
export interface PickedPhoto {
  file: File;
  reduced: ReducedPhoto;
}

/**
 * Single-photo picker: drop zone + "Wybierz zdjęcie" button, per-type hint (AC-12), thumbnail with
 * file name and "Usuń" (AC-11). The object URL of the current photo is revoked on replace and
 * remove only: after submit the store owns it (the chat shows the same thumbnail).
 */
@Component({
  selector: 'app-photo-picker',
  imports: [MatButtonModule],
  templateUrl: './photo-picker.component.html',
  styleUrl: './photo-picker.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PhotoPickerComponent implements OnInit {
  readonly caseType = input<CaseType | null>(null);
  readonly disabled = input(false);
  /** Page tells the picker that the missing photo should be flagged (touched/submitted). */
  readonly showRequired = input(false);
  /** Photo to show on init without a new pick, e.g. the one kept after a failed submit (AC-40). */
  readonly initial = input<PickedPhoto | null>(null);
  readonly photoChange = output<PickedPhoto | null>();

  protected readonly label = FORM_LABELS.photo;
  protected readonly buttonText = t('form.photo.button');
  protected readonly removeText = t('form.photo.remove');
  protected readonly photo = signal<PickedPhoto | null>(null);
  protected readonly pickError = signal<string | null>(null);
  protected readonly dragging = signal(false);

  protected readonly hint = computed(() => {
    switch (this.caseType()) {
      case 'RETURN':
        return t('form.photo.hint.return');
      case 'COMPLAINT':
        return t('form.photo.hint.complaint');
      default:
        return t('form.photo.hint.none');
    }
  });
  protected readonly error = computed(
    () =>
      this.pickError() ?? (this.showRequired() && !this.photo() ? t('form.photo.required') : null),
  );

  private readonly fileInput = viewChild.required('fileInput', {
    read: ElementRef<HTMLInputElement>,
  });
  private readonly pickButton = viewChild.required('pickButton', {
    read: ElementRef<HTMLButtonElement>,
  });

  ngOnInit(): void {
    const initial = this.initial();
    if (initial) this.photo.set(initial);
  }

  focus(): void {
    this.pickButton().nativeElement.focus();
  }

  protected open(): void {
    this.fileInput().nativeElement.click();
  }

  protected onChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) void this.handle(file);
    // Allow picking the same file again after a remove.
    try {
      input.value = '';
    } catch {
      /* some test environments make `files` read-only */
    }
  }

  protected onDragOver(event: DragEvent): void {
    if (this.disabled()) return;
    event.preventDefault();
    this.dragging.set(true);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file && !this.disabled()) void this.handle(file);
  }

  protected remove(): void {
    this.replace(null);
  }

  private async handle(file: File): Promise<void> {
    try {
      const reduced = await reducePhoto(file);
      this.pickError.set(null);
      this.replace({ file, reduced });
    } catch (e) {
      this.pickError.set(e instanceof PhotoReduceError ? e.message : t('form.photo.decode'));
    }
  }

  private replace(next: PickedPhoto | null): void {
    const previous = this.photo();
    if (previous) URL.revokeObjectURL(previous.reduced.objectUrl);
    this.photo.set(next);
    this.photoChange.emit(next);
  }
}
