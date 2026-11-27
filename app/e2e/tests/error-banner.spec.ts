import { test, expect } from './support/fixtures';
import { texts, LABEL } from './support/texts';
import { daysAgo, formatDmy } from './support/dates';
import {
  FLOW_VIEWPORTS, AI_TEST_TIMEOUT, AI_TURN_TIMEOUT, fillCaseForm, photoPath, submitCase,
} from './support/case-flow';

// ORCH starts a second JAR with OPENROUTER_API_KEY=invalid on :8081 (plan §4.5).
const BAD_KEY_URL = process.env['E2E_BAD_KEY_URL'] ?? 'http://localhost:8081';

for (const vp of FLOW_VIEWPORTS) {
  test.describe(`error banner @${vp.name}`, () => {
    test.use({
      viewport: { width: vp.width, height: vp.height },
      // The failing backend legitimately answers with error statuses the browser logs.
      allowedConsoleErrors: [/Failed to load resource/],
    });
    test.describe.configure({ timeout: AI_TEST_TIMEOUT });

    test('invalid key: banner with retry, fields and photo preserved (AC-40)', async ({ page }) => {
      const purchaseDate = daysAgo(4);
      await page.goto(BAD_KEY_URL);
      await fillCaseForm(page, {
        type: 'return',
        category: 'Smartfon',
        model: 'Pixel 8',
        purchaseDate,
        reason: 'Zwrot w ustawowym terminie.',
        photo: photoPath('phone-3.jpeg'),
      });
      await submitCase(page);

      const banner = page.getByText(texts['form.submitError']);
      await expect(banner).toBeVisible({ timeout: AI_TURN_TIMEOUT });
      await expect(page.getByRole('button', { name: texts['common.retry'] })).toBeVisible();
      await expect(page).toHaveURL(/\/$/);

      // Everything entered is still there.
      await expect(page.getByRole('radio', { name: texts['form.caseType.return'] })).toBeChecked();
      await expect(page.getByRole('combobox', { name: LABEL.category })).toContainText('Smartfon');
      await expect(page.getByRole('textbox', { name: LABEL.model })).toHaveValue('Pixel 8');
      await expect(page.getByRole('textbox', { name: LABEL.purchaseDate })).toHaveValue(formatDmy(purchaseDate));
      await expect(page.getByRole('textbox', { name: LABEL.reason })).toHaveValue('Zwrot w ustawowym terminie.');
      await expect(page.getByTestId('photo-thumbnail')).toBeVisible();
      await expect(page.getByText('phone-3.jpeg')).toBeVisible();
    });
  });
}
