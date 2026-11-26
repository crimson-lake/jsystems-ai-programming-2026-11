import { test, expect } from './support/fixtures';
import { daysAgo, polishDateRegex } from './support/dates';
import {
  flowSuite, photoPath, sendChatMessage, submitAndWaitForFirstReply, waitForAssistantComplete,
} from './support/case-flow';

// AC-34: every reply uses the full case context. The date comes from the form only.
flowSuite('complaint follow-up', () => {
  test('COMPLAINT + laptop-1.png, then "Jaką datę zakupu podałem?"', async ({ page }) => {
    const purchaseDate = daysAgo(100);
    await submitAndWaitForFirstReply(page, {
      type: 'complaint',
      category: 'Laptop',
      model: 'ThinkPad T14',
      purchaseDate,
      reason: 'Urządzenie działa niestabilnie i samo się wyłącza.',
      photo: photoPath('laptop-1.png'),
    });

    await sendChatMessage(page, 'Jaką datę zakupu podałem?');
    const reply = await waitForAssistantComplete(page, 1);
    await expect(reply).toContainText(polishDateRegex(purchaseDate));
  });
});
