import { test, expect } from './support/fixtures';
import { texts } from './support/texts';
import { daysAgo } from './support/dates';
import { flowSuite, isBefore, photoPath, submitAndWaitForFirstReply } from './support/case-flow';

// AC-26 via guardrail G1: the safety wording comes from the form reason, not the photo.
flowSuite('safety wording', () => {
  test('reason "Bateria spuchła i czuć swąd": escalated, safety block first', async ({ page }) => {
    const message = await submitAndWaitForFirstReply(page, {
      type: 'return',
      category: 'Smartfon',
      model: 'Galaxy S22',
      purchaseDate: daysAgo(3),
      reason: 'Bateria spuchła i czuć swąd',
      photo: photoPath('phone-3.jpeg'),
    });

    await expect(message.getByTestId('decision-badge')).toHaveText(texts['label.ESCALATED']);

    const safety = message.getByTestId('safety-block');
    await expect(safety).toBeVisible();
    await expect(safety).toContainText('Ze względów bezpieczeństwa:');
    await expect(safety).toContainText('przestań używać urządzenia');
    expect(await safety.innerText(), 'markdown bold rendered, not raw').not.toContain('**');

    // First segment: the first element carrying a segment test id inside the message.
    await expect(message.locator('[data-testid]').first()).toHaveAttribute('data-testid', 'safety-block');
    for (const later of ['assistant-greeting', 'decision-badge', 'disclaimer']) {
      expect(await isBefore(safety, message.getByTestId(later)), `safety block precedes ${later}`).toBe(true);
    }
    await expect(message.getByTestId('decision-change-block')).toHaveCount(0);
  });
});
