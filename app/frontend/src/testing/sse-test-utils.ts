import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * Test-only helpers around the recorded backend SSE bytes (src/testing/captures, plan T26).
 * They never create event payloads: they only re-chunk, truncate and splice recorded blocks.
 * (No vitest imports: this file is part of the app tsconfig, which has no test types.)
 */

const encoder = new TextEncoder();
const decoder = new TextDecoder();

export function readRecording(name: string): Uint8Array {
  return readFileSync(join(process.cwd(), 'src', 'testing', 'captures', name));
}

/** One recorded SSE block (event or comment), `raw` includes the terminating blank line. */
export interface RecordedBlock {
  /** `event:` field; null for comment blocks such as `: ping`. */
  type: string | null;
  raw: string;
  /** Parsed `data:` payload of the recorded block (independent oracle for the parser under test). */
  data: unknown;
}

export function recordedBlocks(name: string): RecordedBlock[] {
  return decoder
    .decode(readRecording(name))
    .split('\n\n')
    .filter((block) => block.length > 0)
    .map((block) => {
      const lines = block.split('\n');
      const eventLine = lines.find((l) => l.startsWith('event:'));
      const dataLine = lines.find((l) => l.startsWith('data:'));
      return {
        type: eventLine ? eventLine.slice('event:'.length).trim() : null,
        raw: block + '\n\n',
        data: dataLine ? JSON.parse(dataLine.slice('data:'.length).trim()) : undefined,
      };
    });
}

/** Blocks of a recording that are real events (comments such as `: ping` are dropped). */
export function recordedEvents(name: string): RecordedBlock[] {
  return recordedBlocks(name).filter((b) => b.type !== null);
}

export function ofType(blocks: RecordedBlock[], ...types: string[]): RecordedBlock[] {
  return blocks.filter((b) => b.type !== null && types.includes(b.type));
}

/** Concatenates recorded blocks (a splice) into SSE bytes. */
export function splice(...parts: RecordedBlock[][]): Uint8Array {
  return encoder.encode(parts.flat().map((b) => b.raw).join(''));
}

export function chunkEvery(bytes: Uint8Array, size: number): Uint8Array[] {
  const chunks: Uint8Array[] = [];
  for (let i = 0; i < bytes.length; i += size) chunks.push(bytes.slice(i, i + size));
  return chunks;
}

export function splitAt(bytes: Uint8Array, ...positions: number[]): Uint8Array[] {
  const cuts = [0, ...positions, bytes.length];
  const chunks: Uint8Array[] = [];
  for (let i = 0; i < cuts.length - 1; i++) chunks.push(bytes.slice(cuts[i], cuts[i + 1]));
  return chunks;
}

export interface SseResponseOptions {
  status?: number;
  /** Keep the body open after the last chunk (a server that stops talking). */
  hang?: boolean;
  /** The fetch `signal`: aborting it errors the body like a real fetch does. */
  signal?: AbortSignal | null;
}

export function sseResponse(chunks: Uint8Array[], options: SseResponseOptions = {}): Response {
  const { status = 200, hang = false, signal } = options;
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(chunk);
      if (!hang) controller.close();
      signal?.addEventListener('abort', () => {
        try {
          controller.error(new DOMException('The operation was aborted.', 'AbortError'));
        } catch {
          // already closed
        }
      });
    },
  });
  return new Response(body, { status, headers: { 'content-type': 'text/event-stream' } });
}

/** A recorded pre-stream HTTP error (`http-*.json`, plan T27): status, headers and the verbatim body text. */
export interface RecordedHttp {
  status: number;
  headers: Record<string, string>;
  body: string;
}

export function recordedHttp(name: string): RecordedHttp {
  return JSON.parse(decoder.decode(readRecording(name))) as RecordedHttp;
}

/** Fake fetch `Response` with the recorded status, headers and body bytes. */
export function httpResponse(name: string): Response {
  const { status, headers, body } = recordedHttp(name);
  return new Response(body, { status, headers });
}
