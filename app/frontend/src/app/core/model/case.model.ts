/** Case types as sent to the backend (ADR-002 section 4). */
export type CaseType = 'RETURN' | 'COMPLAINT';

export const CATEGORY_CODES = [
  'SMARTPHONE',
  'LAPTOP',
  'TABLET',
  'DESKTOP',
  'MONITOR',
  'TV',
  'AUDIO',
  'WEARABLE',
  'OTHER',
] as const;

export type CategoryCode = (typeof CATEGORY_CODES)[number];

/** Polish category labels (ADR-003 section 4). */
export const CATEGORY_LABELS: Readonly<Record<CategoryCode, string>> = {
  SMARTPHONE: 'Smartfon',
  LAPTOP: 'Laptop',
  TABLET: 'Tablet',
  DESKTOP: 'Komputer stacjonarny',
  MONITOR: 'Monitor',
  TV: 'Telewizor',
  AUDIO: 'Słuchawki / Audio',
  WEARABLE: 'Smartwatch / Opaska',
  OTHER: 'Inne',
};

/** Form value in the UI; `purchaseDate` is sent as a local `YYYY-MM-DD` (ADR-003 section 3.3). */
export interface CaseFormValue {
  caseType: CaseType;
  category: CategoryCode;
  model: string;
  purchaseDate: Date;
  reason: string;
  photo: File;
}

/** Photo after client-side reduction to JPEG (ADR-003 section 3.3). */
export interface ReducedPhoto {
  blob: Blob;
  objectUrl: string;
  originalName: string;
  width: number;
  height: number;
}
