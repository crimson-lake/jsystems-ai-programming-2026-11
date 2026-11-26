import { test, expect } from './support/fixtures';
import { DECISION_LABELS, SECTION_HEADINGS, texts } from './support/texts';
import { daysAgo, polishDateRegex } from './support/dates';
import {
  flowSuite, openSummary, photoPath, submitAndWaitForFirstReply, assistantMessages,
} from './support/case-flow';

const MODEL = 'Galaxy S23';

// Structure and form-driven outcomes only: the decision label is never predicted (D1).
flowSuite('return happy path', () => {
  test('RETURN + phone-1.jpg: greeting, badge, sections, disclaimer, summary', async ({ page }) => {
    const purchaseDate = daysAgo(3);
    const message = await submitAndWaitForFirstReply(page, {
      type: 'return',
      category: 'Smartfon',
      model: MODEL,
      purchaseDate,
      photo: photoPath('phone-1.jpg'),
    });

    await expect(message).toContainText(texts['chat.greeting']);

    const badge = message.getByTestId('decision-badge');
    await expect(badge).toBeVisible();
    const badgeText = (await badge.innerText()).trim();
    expect(DECISION_LABELS, 'badge shows one of the four labels').toContain(badgeText);

    for (const heading of SECTION_HEADINGS) {
      await expect(message.getByRole('heading', { name: heading })).toBeVisible();
    }
    await expect(message.getByTestId('disclaimer')).toContainText(texts['chat.disclaimer']);
    await expect(assistantMessages(page)).toHaveCount(1);

    const summary = await openSummary(page);
    await expect(summary).toContainText(texts['form.caseType.return']);
    await expect(summary).toContainText('Smartfon');
    await expect(summary).toContainText(MODEL);
    await expect(summary).toContainText(polishDateRegex(purchaseDate));
    const thumbnail = summary.getByTestId('summary-thumbnail');
    await expect(thumbnail).toBeVisible();
    expect(await thumbnail.evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0)).toBe(true);
    await expect(summary.getByTestId('summary-decision-badge')).toHaveText(badgeText);
  });
});

// The health endpoint is part of the coverage proof (ADR-000 §10).
test('GET /actuator/health responds UP', async ({ request, netlog }) => {
  const response = await request.get('/actuator/health');
  netlog.record({
    method: 'GET',
    path: '/actuator/health',
    status: response.status(),
    contentType: response.headers()['content-type'] ?? null,
    hasPhotoPart: false,
    hasTextPart: false,
  });
  expect(response.status()).toBe(200);
  expect((await response.json()).status).toBe('UP');
});
