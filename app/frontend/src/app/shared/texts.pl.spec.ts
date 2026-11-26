import { TEXTS, t } from './texts.pl';

// PRD section 11, disclaimer, copied character for character.
const PRD_DISCLAIMER =
  'Ta decyzja jest wstępna i została przygotowana automatycznie przez asystenta AI. Ostateczną decyzję podejmie zespół serwisu po oględzinach urządzenia. Nie ogranicza to Twoich praw wynikających z przepisów o ochronie konsumentów.';

describe('texts.pl', () => {
  it('has the disclaimer exactly as in PRD section 11', () => {
    expect(t('chat.disclaimer')).toBe(PRD_DISCLAIMER);
  });

  it('keeps the bold marker of the safety text', () => {
    expect(t('chat.safety').startsWith('**Ze względów bezpieczeństwa:**')).toBe(true);
  });

  it('has a non-empty text for every key', () => {
    for (const [key, value] of Object.entries(TEXTS)) {
      expect(value.trim(), key).not.toBe('');
    }
  });
});
