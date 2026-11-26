import { CdkTextareaAutosize, TextFieldModule } from '@angular/cdk/text-field';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ErrorStateMatcher } from '@angular/material/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { PhotoReduceError, reducePhoto } from '../../core/image/photo-reducer';
import { ReducedPhoto } from '../../core/model';
import { CaseStore, ComposerRejection, MAX_MESSAGE_LENGTH } from '../../core/state/case.store';
import { TextKey, t } from '../../shared/texts.pl';

/**
 * Chat composer (ADR-003 3.3): autosizing textarea (Enter sends, Shift+Enter adds a line), counter,
 * photo attach with a removable preview, send. The store decides whether sending is possible
 * (AC-38) and whether another photo is allowed (AC-33); server refusals come back through
 * `store.composerRejection` (ADR-003 3.7).
 */
@Component({
  selector: 'app-composer',
  imports: [MatButtonModule, MatFormFieldModule, MatInputModule, TextFieldModule],
  templateUrl: './composer.component.html',
  styleUrl: './composer.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComposerComponent {
  protected readonly store = inject(CaseStore);
  private readonly injector = inject(Injector);

  protected readonly label = t('chat.composer.label');
  protected readonly attachText = t('chat.composer.attach');
  protected readonly sendText = t('chat.composer.send');
  protected readonly removeLabel = t('chat.composer.removePhoto');
  protected readonly removeText = t('form.photo.remove');
  protected readonly limitText = t('chat.photoLimit');
  protected readonly max = MAX_MESSAGE_LENGTH;

  protected readonly text = signal('');
  protected readonly photo = signal<ReducedPhoto | null>(null);
  private readonly manualError = signal<TextKey | null>(null);

  protected readonly tooLong = computed(() => this.text().length > MAX_MESSAGE_LENGTH);
  protected readonly hasContent = computed(() => this.text().trim().length > 0 || !!this.photo());
  protected readonly canSubmit = computed(
    () => this.store.canSend() && this.hasContent() && !this.tooLong(),
  );
  protected readonly errorText = computed(() => {
    const key = this.tooLong() ? 'chat.message.tooLong' : this.manualError();
    return key ? t(key) : null;
  });
  protected readonly describedBy = computed(() =>
    ['composer-counter', this.errorText() ? 'composer-error' : null].filter(Boolean).join(' '),
  );
  /** Marks the field invalid while an error is shown (there is no form control to hold the state). */
  protected readonly matcher: ErrorStateMatcher = { isErrorState: () => !!this.errorText() };

  private readonly autosize = viewChild(CdkTextareaAutosize);
  private readonly field = viewChild.required<ElementRef<HTMLTextAreaElement>>('field');
  private readonly fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');

  constructor() {
    effect(() => {
      const rejection = this.store.composerRejection();
      if (rejection) untracked(() => this.applyRejection(rejection));
    });
    // A staged photo cannot be sent once the limit is reached (it would get a 422).
    effect(() => {
      if (!this.store.canAttachPhoto()) untracked(() => this.removePhoto());
    });
  }

  protected onInput(event: Event): void {
    this.text.set((event.target as HTMLTextAreaElement).value);
    this.manualError.set(null);
  }

  protected onEnter(event: Event): void {
    const key = event as KeyboardEvent;
    if (key.shiftKey || key.isComposing) return;
    key.preventDefault();
    this.submit();
  }

  protected submit(): void {
    if (!this.store.canSend() || this.tooLong()) return;
    if (!this.hasContent()) {
      this.manualError.set('chat.message.empty');
      return;
    }
    const photo = this.photo() ?? undefined;
    this.store.sendMessage(this.text().trim(), photo);
    // The store owns the photo (and its object URL) from here on: the bubble shows it.
    this.photo.set(null);
    this.text.set('');
    this.manualError.set(null);
    afterNextRender(
      () => {
        this.autosize()?.resizeToFitContent(true);
        this.field().nativeElement.focus();
      },
      { injector: this.injector },
    );
  }

  protected openFilePicker(): void {
    this.fileInput().nativeElement.click();
  }

  protected async onFileChange(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    try {
      input.value = '';
    } catch {
      /* some test environments make `files` read-only */
    }
    if (!file) return;
    try {
      const reduced = await reducePhoto(file);
      this.removePhoto();
      this.photo.set(reduced);
      this.manualError.set(null);
    } catch (e) {
      this.manualError.set(e instanceof PhotoReduceError ? this.photoErrorKey(e) : 'form.photo.decode');
    }
  }

  protected removePhoto(): void {
    const photo = this.photo();
    if (!photo) return;
    URL.revokeObjectURL(photo.objectUrl);
    this.photo.set(null);
  }

  private photoErrorKey(error: PhotoReduceError): TextKey {
    switch (error.code) {
      case 'INVALID_TYPE':
        return 'form.photo.type';
      case 'TOO_LARGE':
        return 'form.photo.size';
      default:
        return 'form.photo.decode';
    }
  }

  /** The send was refused: take the text back (unless the customer typed anew) and drop the preview. */
  private applyRejection(rejection: ComposerRejection): void {
    if (this.text() === '') this.text.set(rejection.text);
    this.removePhoto();
    // The photo-limit case is shown as the standing hint, not as an error.
    this.manualError.set(rejection.errorKey === 'chat.photoLimit' ? null : rejection.errorKey);
    this.store.clearComposerRejection();
  }
}
