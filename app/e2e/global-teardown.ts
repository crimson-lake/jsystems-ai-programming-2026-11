import * as fs from 'node:fs';
import * as path from 'node:path';

// E2E coverage proof (ADR-000 §10, ADR-003 §8): fails the run unless every endpoint
// appears in the browser network logs and the backend log has ai.call lines with
// status=OK for VISION (form + chat), DECISION and STREAM. TRIAGE (typesafe/jev-1.14, assist mode) is required too.
// Self-contained on purpose (fs/path only) so it can run outside Playwright.

interface NetEntry {
  method: string;
  path: string;
  status: number | null;
  hasPhotoPart: boolean;
  hasTextPart: boolean;
}
interface AiCall { step: string; caseId: string; model: string; status: string }

const NETWORK_DIR = path.resolve(__dirname, 'test-results/network');
const APP_LOG = process.env['E2E_APP_LOG'] ?? path.resolve(__dirname, 'test-results/app.log');
const AI_CALL = /ai\.call step=(\w+) caseId=(\S+) model=(\S+) status=(\w+)/;

function readNetwork(): NetEntry[] {
  if (!fs.existsSync(NETWORK_DIR)) return [];
  return fs
    .readdirSync(NETWORK_DIR)
    .filter((f: string) => f.endsWith('.json'))
    .flatMap((f: string) => JSON.parse(fs.readFileSync(path.join(NETWORK_DIR, f), 'utf8')) as NetEntry[]);
}

function readAiCalls(missing: string[]): AiCall[] {
  if (!fs.existsSync(APP_LOG)) {
    missing.push(`backend log not found: ${APP_LOG} (set E2E_APP_LOG)`);
    return [];
  }
  const calls: AiCall[] = [];
  for (const line of fs.readFileSync(APP_LOG, 'utf8').split('\n')) {
    const m = AI_CALL.exec(line);
    if (m) calls.push({ step: m[1], caseId: m[2], model: m[3], status: m[4] });
  }
  return calls;
}

export default async function globalTeardown(): Promise<void> {
  const missing: string[] = [];
  const net = readNetwork();
  if (net.length === 0) missing.push(`no network logs in ${NETWORK_DIR}`);

  const ok = (e: NetEntry): boolean => e.status !== null && e.status >= 200 && e.status < 300;
  const endpoints: Array<[string, (e: NetEntry) => boolean]> = [
    ['POST /api/cases', (e) => e.method === 'POST' && e.path === '/api/cases' && ok(e)],
    ['POST /api/cases/{id}/messages (text only)',
      (e) => e.method === 'POST' && /^\/api\/cases\/[^/]+\/messages$/.test(e.path) && !e.hasPhotoPart && e.hasTextPart && ok(e)],
    ['POST /api/cases/{id}/messages (with photo part)',
      (e) => e.method === 'POST' && /^\/api\/cases\/[^/]+\/messages$/.test(e.path) && e.hasPhotoPart && ok(e)],
    ['DELETE /api/cases/{id} -> 204', (e) => e.method === 'DELETE' && /^\/api\/cases\/[^/]+$/.test(e.path) && e.status === 204],
    ['GET /actuator/health', (e) => e.method === 'GET' && e.path === '/actuator/health' && e.status === 200],
  ];
  for (const [name, match] of endpoints) {
    const hits = net.filter(match).length;
    console.log(`[coverage] endpoint ${name}: ${hits} call(s)`);
    if (hits === 0) missing.push(`endpoint never called successfully: ${name}`);
  }

  const calls = readAiCalls(missing);
  const okCalls = (step: string): AiCall[] => calls.filter((c) => c.step === step && c.status === 'OK');
  for (const step of ['TRIAGE', 'VISION', 'DECISION', 'STREAM']) {
    const n = okCalls(step).length;
    console.log(`[coverage] ai.call ${step} status=OK: ${n}`);
    if (n === 0) missing.push(`no ai.call step=${step} status=OK in ${APP_LOG}`);
  }
  // VISION on the form (first turn) and in chat (photo turn): one case with two OK lines.
  const visionPerCase = new Map<string, number>();
  for (const c of okCalls('VISION')) visionPerCase.set(c.caseId, (visionPerCase.get(c.caseId) ?? 0) + 1);
  const formAndChat = [...visionPerCase.values()].some((n) => n >= 2);
  console.log(`[coverage] VISION form + chat on the same case: ${formAndChat ? 'observed' : 'NOT observed'}`);
  if (!formAndChat) missing.push('no case has two VISION status=OK lines (form + chat photo)');

  const reask = calls.filter((c) => c.step === 'DECISION_REASK').length;
  console.log(`[coverage] DECISION_REASK: ${reask > 0 ? `observed (${reask})` : 'not observed'}`);

  if (missing.length > 0) {
    throw new Error(`E2E coverage proof failed:\n - ${missing.join('\n - ')}`);
  }
  console.log('[coverage] proof OK');
}
