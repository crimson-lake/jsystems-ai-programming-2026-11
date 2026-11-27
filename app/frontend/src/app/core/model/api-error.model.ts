export type ApiErrorCode =
  | 'VALIDATION'
  | 'CASE_NOT_FOUND'
  | 'CASE_BUSY'
  | 'PHOTO_LIMIT'
  | 'PAYLOAD_TOO_LARGE'
  | 'INTERNAL';

export interface FieldError {
  field: string;
  message: string;
}

/** Error body of non-streaming responses (ADR-002 section 4). */
export interface ApiError {
  code: ApiErrorCode;
  message?: string;
  fieldErrors?: FieldError[];
}
