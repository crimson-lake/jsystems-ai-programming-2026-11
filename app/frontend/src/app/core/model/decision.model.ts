export type DecisionLabel = 'ACCEPTED_PRELIMINARY' | 'REJECTED' | 'NEEDS_MORE_INFO' | 'ESCALATED';

export type SuggestOtherCaseType = 'NONE' | 'RETURN' | 'COMPLAINT';

/** Payload of the SSE `decision` event, identical field names to the Java `DecisionEvent`. */
export interface DecisionView {
  label: DecisionLabel;
  previousLabel?: DecisionLabel | null;
  changed: boolean;
  changeReasonPl?: string | null;
  safety: boolean;
  suggestOtherCaseType: SuggestOtherCaseType;
  ruleIds: string[];
}
