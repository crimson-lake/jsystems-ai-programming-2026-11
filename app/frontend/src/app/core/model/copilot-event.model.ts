import { DecisionView } from './decision.model';

export type ProgressStep = 'UPLOAD_RECEIVED' | 'ANALYZING_PHOTO' | 'PREPARING_DECISION' | 'WRITING_REPLY';

export type StreamErrorCode = 'AI_UNAVAILABLE' | 'TIMEOUT';

/** SSE events of both streaming endpoints; payload field names equal the Java `*Event` records. */
export type CopilotEvent =
  | { type: 'case'; data: { caseId: string } }
  | { type: 'progress'; data: { step: ProgressStep } }
  | { type: 'decision'; data: DecisionView }
  | { type: 'delta'; data: { text: string } }
  | { type: 'done'; data: { messageId: string } }
  | { type: 'error'; data: { code: StreamErrorCode; message: string } };

export type CopilotEventType = CopilotEvent['type'];
