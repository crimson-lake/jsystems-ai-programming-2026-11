import { DecisionView } from './decision.model';

export type UiMessageStatus = 'sending' | 'streaming' | 'complete' | 'failed';

export interface UiMessage {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  photoUrl?: string;
  createdAt: Date;
  status: UiMessageStatus;
  clientMessageId?: string;
  decision?: DecisionView;
  isFirst: boolean;
}
