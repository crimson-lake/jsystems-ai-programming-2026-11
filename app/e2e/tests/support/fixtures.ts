import { test as base, expect } from '@playwright/test';
import * as fs from 'node:fs';
import * as path from 'node:path';

/** One observed request to the backend (path starts with /api or /actuator). */
export interface NetEntry {
  method: string;
  path: string;
  status: number | null;
  contentType: string | null;
  hasPhotoPart: boolean;
  hasTextPart: boolean;
  test: string;
}

export const NETWORK_LOG_DIR = path.resolve(__dirname, '../../test-results/network');

export class NetLog {
  readonly entries: NetEntry[] = [];
  constructor(readonly title: string) {}

  /** For API calls made outside the page (the `request` fixture). */
  record(e: Omit<NetEntry, 'test'>): void {
    this.entries.push({ ...e, test: this.title });
  }

  find(method: string, pathPattern: RegExp, extra?: (e: NetEntry) => boolean): NetEntry[] {
    return this.entries.filter(
      (e) => e.method === method && pathPattern.test(e.path) && (extra ? extra(e) : true),
    );
  }
}

interface Fixtures {
  /** Console messages of type error are collected and asserted empty after each test. */
  allowedConsoleErrors: RegExp[];
  netlog: NetLog;
}

export const test = base.extend<Fixtures>({
  allowedConsoleErrors: [[], { option: true }],

  // Auto fixture: runs for every test, so every spec asserts "no console errors"
  // and writes its network log for the coverage proof in global-teardown.ts.
  netlog: [
    async ({ page, allowedConsoleErrors }, use, testInfo) => {
      const netlog = new NetLog(testInfo.titlePath.slice(1).join(' > '));
      const consoleErrors: string[] = [];
      const pending = new Map<unknown, NetEntry>();

      page.on('console', (msg) => {
        if (msg.type() === 'error') consoleErrors.push(msg.text());
      });
      page.on('pageerror', (err) => consoleErrors.push(`pageerror: ${err.message}`));
      page.on('request', (req) => {
        const url = new URL(req.url());
        if (!/^\/(api|actuator)(\/|$)/.test(url.pathname)) return;
        const buffer = req.postDataBuffer();
        const raw = buffer?.toString('latin1') ?? '';
        // Chromium withholds the body of a multipart request that contains a File/Blob (null buffer),
        // so a multipart request without readable body is the photo upload; backend log `photo=true` corroborates.
        const blobMultipart = buffer === null && (req.headers()['content-type'] ?? '').startsWith('multipart/form-data');
        const entry: NetEntry = {
          method: req.method(),
          path: url.pathname,
          status: null,
          contentType: null,
          hasPhotoPart: blobMultipart || raw.includes('name="photo"'),
          hasTextPart: raw.includes('name="text"'),
          test: netlog.title,
        };
        netlog.entries.push(entry);
        pending.set(req, entry);
      });
      page.on('response', (res) => {
        const entry = pending.get(res.request());
        if (entry) {
          entry.status = res.status();
          entry.contentType = res.headers()['content-type'] ?? null;
        }
      });

      await use(netlog);

      fs.mkdirSync(NETWORK_LOG_DIR, { recursive: true });
      const safe = testInfo.titlePath.slice(1).join('_').replace(/[^\w.-]+/g, '_').slice(0, 120);
      fs.writeFileSync(
        path.join(NETWORK_LOG_DIR, `${testInfo.project.name}-w${testInfo.workerIndex}-r${testInfo.retry}-${safe}.json`),
        JSON.stringify(netlog.entries, null, 2),
      );

      const unexpected = consoleErrors.filter((m) => !allowedConsoleErrors.some((re) => re.test(m)));
      expect(unexpected, 'browser console errors').toEqual([]);
    },
    { auto: true },
  ],
});

export { expect };
