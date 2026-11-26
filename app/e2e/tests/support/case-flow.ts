import { type Locator, type Page, expect } from '@playwright/test';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { LABEL, texts } from './texts';
import { formatDmy } from './dates';
import { test } from './fixtures';

/** AI turns call real OpenRouter models; give them room (plan: ~120 s per turn). */
export const AI_TURN_TIMEOUT = 120_000;
export const AI_TEST_TIMEOUT = 300_000;

const IMAGE_DIR = path.resolve(__dirname, '../../../../assets/example-images');

/** Real example photo only (assets/example-images/README.md): never generate images. */
export function photoPath(name: 'laptop-1.png' | 'laptop-2.webp' | 'phone-1.jpg' | 'phone-2.jpeg' | 'phone-3.jpeg'): string {
  const p = path.join(IMAGE_DIR, name);
  if (!fs.existsSync(p)) throw new Error(`Example photo missing: ${p}`);
  return p;
}

export const FLOW_VIEWPORTS = [
  { name: '360px', width: 360, height: 800 },
  { name: '1280px', width: 1280, height: 900 },
] as const;

/** Runs the same flow definitions at 360 and 1280 px (config cannot define projects). */
export function flowSuite(title: string, define: () => void): void {
  for (const vp of FLOW_VIEWPORTS) {
    test.describe(`${title} @${vp.name}`, () => {
      test.use({ viewport: { width: vp.width, height: vp.height } });
      test.describe.configure({ timeout: AI_TEST_TIMEOUT });
      define();
    });
  }
}

export interface CaseInput {
  type: 'return' | 'complaint';
  category: string;
  model: string;
  purchaseDate: Date;
  reason?: string;
  photo: string;
}

export const caseTypeLabel = (type: CaseInput['type']): string =>
  type === 'return' ? texts['form.caseType.return'] : texts['form.caseType.complaint'];

export async function fillCaseForm(page: Page, input: CaseInput): Promise<void> {
  await page.getByRole('radio', { name: caseTypeLabel(input.type) }).check();
  // 360 px: the floating mat-label overlays the select and Playwright's hit-test refuses the click; a real mouse click at that point still opens it.
  await page.getByRole('combobox', { name: LABEL.category }).click({ force: true });
  await page.getByRole('option', { name: input.category, exact: true }).click();
  await page.getByRole('textbox', { name: LABEL.model }).fill(input.model);
  const date = page.getByRole('textbox', { name: LABEL.purchaseDate });
  await date.fill(formatDmy(input.purchaseDate));
  await date.blur();
  if (input.reason !== undefined) {
    await page.getByRole('textbox', { name: LABEL.reason }).fill(input.reason);
  }
  await page.locator('input[type="file"]').setInputFiles(input.photo);
  await expect(page.getByTestId('photo-thumbnail')).toBeVisible();
}

export async function openForm(page: Page): Promise<void> {
  await page.goto('/');
  await expect(page.getByRole('button', { name: texts['form.submit'] })).toBeVisible();
}

export async function submitCase(page: Page): Promise<void> {
  await page.getByRole('button', { name: texts['form.submit'] }).click();
}

/** Fills, submits and waits until the first assistant message is complete. */
export async function submitAndWaitForFirstReply(page: Page, input: CaseInput): Promise<Locator> {
  await openForm(page);
  await fillCaseForm(page, input);
  await submitCase(page);
  await page.waitForURL(/\/sprawa/, { timeout: AI_TURN_TIMEOUT });
  return waitForAssistantComplete(page, 0);
}

export const assistantMessages = (page: Page): Locator => page.getByTestId('assistant-message');

/** data-state="complete" is set on `done` (frontend contract, see report). */
export async function waitForAssistantComplete(page: Page, index: number): Promise<Locator> {
  const message = assistantMessages(page).nth(index);
  await expect(message).toHaveAttribute('data-state', 'complete', { timeout: AI_TURN_TIMEOUT });
  return message;
}

/** Below 960 px the summary sits in a collapsed expansion panel "Szczegóły sprawy". */
export async function openSummary(page: Page): Promise<Locator> {
  const summary = page.getByTestId('case-summary');
  if (!(await summary.isVisible())) {
    await page.getByRole('button', { name: texts['chat.summaryTitle'] }).click();
  }
  await expect(summary).toBeVisible();
  return summary;
}

export async function sendChatMessage(page: Page, text: string, photo?: string): Promise<void> {
  if (photo) {
    await page.locator('input[type="file"]').setInputFiles(photo);
    // A human sees the staged chip before pressing send; wait for it so the photo is part of this message.
    await expect(page.getByText(path.basename(photo), { exact: true })).toBeVisible();
  }
  await page.getByRole('textbox', { name: LABEL.composerInput }).fill(text);
  await page.getByRole('button', { name: LABEL.composerSend }).click();
}

/** True when `a` comes before `b` in document order. */
export async function isBefore(a: Locator, b: Locator): Promise<boolean> {
  const handle = await b.elementHandle();
  if (!handle) throw new Error('isBefore: second element not found');
  return a.evaluate((x, y) => !!(x.compareDocumentPosition(y as Node) & Node.DOCUMENT_POSITION_FOLLOWING), handle);
}

export async function noHorizontalScroll(page: Page): Promise<{ scrollWidth: number; clientWidth: number }> {
  return page.evaluate(() => {
    const el = document.scrollingElement ?? document.documentElement;
    return { scrollWidth: el.scrollWidth, clientWidth: el.clientWidth };
  });
}
