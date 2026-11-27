import { MarkdownPipe } from './markdown.pipe';

describe('MarkdownPipe', () => {
  const pipe = new MarkdownPipe();
  const html = (md: string) => pipe.transform(md);
  const dom = (md: string) => {
    const host = document.createElement('div');
    host.innerHTML = html(md);
    return host;
  };

  it('renders headings, lists and bold as formatting (PRD 9.2)', () => {
    const host = dom(
      '### Co widzimy na zdjęciu\n\nTekst **ważny**.\n\n#### Uzasadnienie\n\n1. Pierwszy\n2. Drugi\n\n- punkt\n',
    );
    expect(host.querySelector('h3')?.textContent).toBe('Co widzimy na zdjęciu');
    expect(host.querySelector('h4')?.textContent).toBe('Uzasadnienie');
    expect(host.querySelector('strong')?.textContent).toBe('ważny');
    expect(host.querySelectorAll('ol > li').length).toBe(2);
    expect(host.querySelectorAll('ul > li').length).toBe(1);
  });

  it('removes <script> elements and their content', () => {
    const out = html('Cześć <script>alert(1)</script> koniec');
    expect(out).not.toContain('<script');
    expect(out).not.toContain('alert(1)');
    expect(out).toContain('Cześć');
  });

  it('removes <img onerror> completely', () => {
    const out = html('<img src=x onerror="alert(1)"> tekst');
    expect(out).not.toContain('<img');
    expect(out).not.toContain('onerror');
  });

  it('does not render links, including javascript: URLs', () => {
    const out = html('[kliknij](javascript:alert(1)) i [ok](https://example.com)');
    expect(out).not.toContain('<a');
    expect(out).not.toContain('href');
    expect(out).not.toContain('javascript:');
    expect(out).toContain('kliknij');
  });

  it('keeps allow-listed inline tags only', () => {
    const host = dom('*kursywa* `kod` linia<br>druga <iframe src="x"></iframe><div class="x">blok</div>');
    expect(host.querySelector('em')?.textContent).toBe('kursywa');
    expect(host.querySelector('code')?.textContent).toBe('kod');
    expect(host.querySelector('br')).toBeTruthy();
    expect(host.querySelector('iframe')).toBeNull();
    expect(host.querySelector('div')).toBeNull();
    expect(host.innerHTML).not.toContain('class=');
  });

  it('does not throw on unclosed markdown while streaming', () => {
    for (const partial of ['**pogrubi', '- punkt\n- **ur', '### Nagł', '1. a\n2.', '`kod', '<scr', '']) {
      expect(() => html(partial), partial).not.toThrow();
    }
    expect(html('**pogrubi')).toContain('pogrubi');
  });

  it('returns an empty string for empty, null and undefined input', () => {
    expect(html('')).toBe('');
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
  });
});
