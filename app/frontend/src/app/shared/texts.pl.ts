import texts from './texts.pl.json';

/** All fixed Polish UI strings (ADR-003 section 3.7). The JSON file is the single source. */
export type TextKey = keyof typeof texts;

export const TEXTS: Readonly<Record<TextKey, string>> = texts;

/** Returns the Polish text for a key; unknown keys do not compile. */
export function t(key: TextKey): string {
  return TEXTS[key];
}
