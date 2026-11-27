const MONTHS_GENITIVE = [
  'stycznia', 'lutego', 'marca', 'kwietnia', 'maja', 'czerwca',
  'lipca', 'sierpnia', 'września', 'października', 'listopada', 'grudnia',
];

const pad = (n: number): string => String(n).padStart(2, '0');

export function daysAgo(days: number): Date {
  const d = new Date();
  d.setHours(12, 0, 0, 0);
  d.setDate(d.getDate() - days);
  return d;
}

/** dd.MM.yyyy, the format typed into the date field. */
export function formatDmy(d: Date): string {
  return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${d.getFullYear()}`;
}

export function formatIso(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Accepts dd.MM.yyyy, d.M.yyyy, "d MMMM yyyy" (Polish genitive) and yyyy-MM-dd. */
export function polishDateRegex(d: Date): RegExp {
  const day = d.getDate();
  const month = d.getMonth() + 1;
  const year = d.getFullYear();
  const alternatives = [
    `0?${day}\\.0?${month}\\.${year}`,
    `0?${day}\\s+${MONTHS_GENITIVE[month - 1]}\\s+${year}`,
    `${year}-${pad(month)}-${pad(day)}`,
  ];
  return new RegExp(`(?<![\\d.])(?:${alternatives.join('|')})(?!\\d)`, 'i');
}
