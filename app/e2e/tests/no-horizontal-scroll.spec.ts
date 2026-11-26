import { type Page } from '@playwright/test';
import { test, expect } from './support/fixtures';
import { texts } from './support/texts';
import { daysAgo } from './support/dates';
import {
  AI_TEST_TIMEOUT, noHorizontalScroll, openForm, photoPath, submitAndWaitForFirstReply, submitCase,
} from './support/case-flow';

const WIDTHS = [360, 390, 768, 1280, 1920] as const;

async function expectNoHorizontalScroll(page: Page, width: number, where: string): Promise<void> {
  await page.setViewportSize({ width, height: 900 });
  const { scrollWidth, clientWidth } = await noHorizontalScroll(page);
  expect(scrollWidth, `${where} at ${width}px: scrollWidth <= clientWidth`).toBeLessThanOrEqual(clientWidth);
}

// TAC-003-05 / AC-46. One real submission; the chat is then checked at every width
// by resizing the same page (the case stays in memory).
test.describe('no horizontal scroll', () => {
  test.describe.configure({ timeout: AI_TEST_TIMEOUT });

  test('form, form with validation errors and chat at 360/390/768/1280/1920 px', async ({ page }) => {
    await openForm(page);
    for (const width of WIDTHS) await expectNoHorizontalScroll(page, width, 'empty form');

    await submitCase(page); // empty submit shows every validation error (AC-13)
    await expect(page.getByText(texts['form.caseType.required'])).toBeVisible();
    for (const width of WIDTHS) await expectNoHorizontalScroll(page, width, 'form with errors');

    await page.setViewportSize({ width: 360, height: 900 });
    await page.reload();
    await submitAndWaitForFirstReply(page, {
      type: 'return',
      category: 'Smartfon',
      model: 'Galaxy S23',
      purchaseDate: daysAgo(2),
      photo: photoPath('phone-1.jpg'),
    });
    for (const width of WIDTHS) await expectNoHorizontalScroll(page, width, 'chat');

    await page.setViewportSize({ width: 360, height: 900 });
    await page.getByRole('button', { name: texts['chat.summaryTitle'] }).click();
    await expect(page.getByTestId('case-summary')).toBeVisible();
    await expectNoHorizontalScroll(page, 360, 'chat with the summary panel open');
  });
});
