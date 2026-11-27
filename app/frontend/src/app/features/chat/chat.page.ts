import { BreakpointObserver } from '@angular/cdk/layout';
import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatSidenavModule } from '@angular/material/sidenav';
import { map } from 'rxjs';
import { t } from '../../shared/texts.pl';
import { CaseSummaryComponent } from './case-summary.component';
import { ComposerComponent } from './composer.component';
import { MessageListComponent } from './message-list.component';

/** Below this width the summary becomes a collapsible panel above the chat (PRD 9.2). */
const WIDE_QUERY = '(min-width: 960px)';

/** Screen 2: the decision chat (PRD 9.2, ADR-003 3.3). */
@Component({
  selector: 'app-chat-page',
  imports: [
    NgTemplateOutlet,
    MatExpansionModule,
    MatSidenavModule,
    CaseSummaryComponent,
    ComposerComponent,
    MessageListComponent,
  ],
  templateUrl: './chat.page.html',
  styleUrl: './chat.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatPage {
  protected readonly wide = toSignal(
    inject(BreakpointObserver)
      .observe(WIDE_QUERY)
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );
  protected readonly summaryTitle = t('chat.summaryTitle');
}
