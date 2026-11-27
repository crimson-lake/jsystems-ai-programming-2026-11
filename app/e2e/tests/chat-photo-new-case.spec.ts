import { test, expect } from './support/fixtures';
import { texts, LABEL } from './support/texts';
import { daysAgo } from './support/dates';
import {
  AI_TURN_TIMEOUT, flowSuite, openSummary, photoPath, sendChatMessage,
  submitAndWaitForFirstReply, waitForAssistantComplete,
} from './support/case-flow';

flowSuite('chat photo and new case', () => {
  test('photo in chat is streamed; "Nowa sprawa" deletes the case and empties the form', async ({ page, netlog }) => {
    await submitAndWaitForFirstReply(page, {
      type: 'return',
      category: 'Laptop',
      model: 'MacBook Air',
      purchaseDate: daysAgo(5),
      photo: photoPath('phone-2.jpeg'),
    });

    await sendChatMessage(page, 'Dodaję drugie zdjęcie. Czy coś to zmienia w mojej sprawie?', photoPath('laptop-2.webp'));
    await waitForAssistantComplete(page, 1);

    const posts = netlog.find('POST', /^\/api\/cases\/[^/]+\/messages$/, (e) => e.hasPhotoPart);
    expect(posts.length, 'POST /api/cases/{id}/messages carried a photo part').toBeGreaterThanOrEqual(1);
    expect(posts[0].status).toBe(200);
    expect(posts[0].contentType ?? '', 'reply arrives as an SSE stream').toContain('text/event-stream');
    const caseId = posts[0].path.split('/')[3];

    const summary = await openSummary(page);
    const deleted = page.waitForResponse(
      (r) => r.request().method() === 'DELETE' && r.url().includes(`/api/cases/${caseId}`),
      { timeout: AI_TURN_TIMEOUT },
    );
    await summary.getByRole('button', { name: texts['common.newCase'] }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText(texts['dialog.newCase.text']);
    await dialog.getByRole('button', { name: texts['dialog.newCase.confirm'] }).click();

    expect((await deleted).status()).toBe(204);
    await expect(page).toHaveURL(/\/$/);
    await expect(page.getByRole('radio', { name: texts['form.caseType.return'] })).not.toBeChecked();
    await expect(page.getByRole('radio', { name: texts['form.caseType.complaint'] })).not.toBeChecked();
    await expect(page.getByRole('textbox', { name: LABEL.model })).toHaveValue('');
    await expect(page.getByRole('textbox', { name: LABEL.purchaseDate })).toHaveValue('');
    await expect(page.getByTestId('photo-thumbnail')).toHaveCount(0);
  });
});
