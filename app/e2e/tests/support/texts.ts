import * as fs from 'node:fs';
import * as path from 'node:path';

// Single source of the Polish UI texts: the frontend's own JSON (ADR-003 §3.7).
export const texts: Record<string, string> = JSON.parse(
  fs.readFileSync(path.resolve(__dirname, '../../../frontend/src/app/shared/texts.pl.json'), 'utf8'),
);

export const DECISION_LABELS: string[] = [
  texts['label.ACCEPTED_PRELIMINARY'],
  texts['label.REJECTED'],
  texts['label.NEEDS_MORE_INFO'],
  texts['label.ESCALATED'],
];

// Accessible names that texts.pl.json does not define. The frontend tasks must
// implement labels matching these patterns (AC-47: visible labels).
export const LABEL = {
  category: /Kategoria/,
  model: /Nazwa lub model/,
  purchaseDate: /Data zakupu/,
  reason: /Powód|Opis problemu/,
  composerInput: /Wiadomość/,
  composerAttach: /Dodaj zdjęcie/,
  composerSend: /Wyślij wiadomość/,
} as const;

export const SECTION_HEADINGS = ['Co widzimy na zdjęciu', 'Uzasadnienie', 'Kolejne kroki'] as const;
