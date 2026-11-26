import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { UiMessage } from '../../core/model';
import { CaseStore } from '../../core/state/case.store';
import { TextKey, t } from '../../shared/texts.pl';
import { NewCaseService } from './new-case.service';
import { PhotoDialogComponent } from './photo-dialog.component';

/** Customer bubble: text, photo thumbnail, time and the failed state with retry (AC-41). */
@Component({
  selector: 'app-user-message',
  imports: [DatePipe, MatButtonModule],
  templateUrl: './user-message.component.html',
  styleUrl: './user-message.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserMessageComponent {
  readonly message = input.required<UiMessage>();

  private readonly store = inject(CaseStore);
  private readonly newCase = inject(NewCaseService);
  private readonly dialog = inject(MatDialog);

  protected readonly photoAlt = t('form.label.photo');
  protected readonly enlargeLabel = t('chat.photo.enlarge');
  protected readonly retryText = t('common.retry');
  protected readonly newCaseText = t('common.newCase');

  protected readonly failed = computed(() => this.message().status === 'failed');
  private readonly errorKey = computed<TextKey>(
    () => this.store.messageErrors()[this.message().id] ?? 'chat.error.generic',
  );
  protected readonly errorText = computed(() => t(this.errorKey()));
  /** 404: nothing left to retry, only a fresh case helps (ADR-003 3.7). */
  protected readonly expired = computed(() => this.errorKey() === 'chat.error.expired');

  protected enlarge(): void {
    const url = this.message().photoUrl;
    if (url) this.dialog.open(PhotoDialogComponent, { data: { url }, maxWidth: '92vw' });
  }

  protected retry(): void {
    this.store.retry(this.message().id);
  }

  protected startNewCase(): void {
    this.newCase.start();
  }
}
