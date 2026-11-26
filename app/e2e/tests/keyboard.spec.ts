import { type Locator, type Page } from '@playwright/test';
import { test, expect } from './support/fixtures';
import { LABEL, texts } from './support/texts';
import { daysAgo, formatDmy } from './support/dates';
import {
  AI_TURN_TIMEOUT, flowSuite, openForm, photoPath, waitForAssistantComplete,
} from './support/case-flow';

/** Presses Tab until `target` has focus; the focus must be a visible keyboard focus (AC-47). */
async function tabTo(page: Page, target: Locator, max = 60): Promise<void> {
  for (let i = 0; i < max; i++) {
    const focused = await target
      .evaluate((el) => el === document.activeElement, undefined, { timeout: 2_000 })
      .catch(() => false);
    if (focused) {
      expect(await target.evaluate((el) => el.matches(':focus-visible')), 'keyboard focus is visible').toBe(true);
      await expect(target).toBeInViewport();
      return;
    }
    await page.keyboard.press('Tab');
  }
  throw new Error(`Tab never reached ${target}`);
}

// AC-47: the whole flow with the keyboard only. setFiles on the native file chooser is
// allowed because the OS dialog is outside the browser.
flowSuite('keyboard only', () => {
  test('complete a return and reach the chat controls with Tab/Space/Enter/arrows', async ({ page }) => {
    const purchaseDate = daysAgo(2);
    await openForm(page);

    // Case type: arrows inside the radio group (Reklamacja, Zwrot).
    await tabTo(page, page.getByRole('radio', { name: texts['form.caseType.complaint'] }));
    await page.keyboard.press('ArrowRight');
    await expect(page.getByRole('radio', { name: texts['form.caseType.return'] })).toBeChecked();

    // Category: open with Enter, jump to the first option (Smartfon), choose with Enter.
    const category = page.getByRole('combobox', { name: LABEL.category });
    await tabTo(page, category);
    await page.keyboard.press('Enter');
    await expect(page.getByRole('listbox')).toBeVisible();
    await page.keyboard.press('Home');
    await page.keyboard.press('Enter');
    await expect(category).toContainText('Smartfon');

    await tabTo(page, page.getByRole('textbox', { name: LABEL.model }));
    await page.keyboard.type('Galaxy S23');
    await tabTo(page, page.getByRole('textbox', { name: LABEL.purchaseDate }));
    await page.keyboard.type(formatDmy(purchaseDate));
    await tabTo(page, page.getByRole('textbox', { name: LABEL.reason }));
    await page.keyboard.type('Zwrot w ustawowym terminie.');

    // Photo: the button opens the native chooser via Enter.
    await tabTo(page, page.getByRole('button', { name: texts['form.photo.button'] }));
    const [chooser] = await Promise.all([page.waitForEvent('filechooser'), page.keyboard.press('Enter')]);
    await chooser.setFiles(photoPath('phone-1.jpg'));
    await expect(page.getByTestId('photo-thumbnail')).toBeVisible();
    await expect(page.getByRole('button', { name: texts['form.photo.remove'] })).toBeVisible();

    await tabTo(page, page.getByRole('button', { name: texts['form.submit'] }));
    await page.keyboard.press('Enter');

    await page.waitForURL(/\/sprawa/, { timeout: AI_TURN_TIMEOUT });
    await waitForAssistantComplete(page, 0);

    // Chat controls: labelled and reachable by keyboard.
    const input = page.getByRole('textbox', { name: LABEL.composerInput });
    await tabTo(page, input);
    await page.keyboard.type('Dziękuję');
    await tabTo(page, page.getByRole('button', { name: LABEL.composerAttach }));
    await tabTo(page, page.getByRole('button', { name: LABEL.composerSend }));
    // Below 960 px "Nowa sprawa" lives in the collapsed "Szczegóły sprawy" panel: a keyboard user opens it first.
    if (!(await page.getByTestId('case-summary').isVisible())) {
      const panel = page.getByRole('button', { name: texts['chat.summaryTitle'] });
      await tabTo(page, panel);
      await page.keyboard.press('Enter');
      await expect(panel).toHaveAttribute('aria-expanded', 'true');
    }
    await tabTo(page, page.getByRole('button', { name: texts['common.newCase'] }).first());
  });
});
