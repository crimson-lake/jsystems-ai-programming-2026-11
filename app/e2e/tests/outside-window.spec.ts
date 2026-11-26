import { test, expect } from './support/fixtures';
import { texts } from './support/texts';
import { daysAgo } from './support/dates';
import { flowSuite, photoPath, submitAndWaitForFirstReply } from './support/case-flow';

// PRD §4.3 / ADR-003 §8: a 45-day-old return is rejected citing 45 and 30, or, if the
// model sees a hazard, escalated with the safety block (G1). Photo content is never assumed.
flowSuite('outside the return window', () => {
  test('RETURN 45 days ago: Odrzucone (45 and 30 mentioned) or safety escalation', async ({ page }) => {
    const message = await submitAndWaitForFirstReply(page, {
      type: 'return',
      category: 'Smartfon',
      model: 'Galaxy S21',
      purchaseDate: daysAgo(45),
      photo: photoPath('phone-2.jpeg'),
    });

    const badge = message.getByTestId('decision-badge');
    await expect(badge).toBeVisible();
    const label = (await badge.innerText()).trim();

    if (label === texts['label.REJECTED']) {
      const text = await message.innerText();
      expect(text, 'rejection mentions the 45 days since purchase').toMatch(/(?<!\d)45(?!\d)/);
      expect(text, 'rejection mentions the 30-day window').toMatch(/(?<!\d)30(?!\d)/);
    } else if (label === texts['label.ESCALATED']) {
      await expect(message.getByTestId('safety-block')).toBeVisible();
    } else {
      throw new Error(`Unexpected label "${label}" for a 45-day-old return; expected Odrzucone or Przekazane do specjalisty`);
    }
  });
});
