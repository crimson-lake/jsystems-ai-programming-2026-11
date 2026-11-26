import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const HEX = /#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3,4})\b/;

function scssFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return scssFiles(path);
    return path.endsWith('.scss') ? [path] : [];
  });
}

describe('TAC-003-02: no hex colors in component SCSS', () => {
  const files = scssFiles(join(process.cwd(), 'src', 'app'));

  it('finds the component stylesheets', () => {
    expect(files.length).toBeGreaterThan(0);
  });

  it('contains no hex color literal under src/app', () => {
    const offenders = files.filter((f) =>
      readFileSync(f, 'utf8')
        .split('\n')
        .some((line: string) => HEX.test(line.replace(/\/\/.*$/, ''))),
    );
    expect(offenders).toEqual([]);
  });
});
