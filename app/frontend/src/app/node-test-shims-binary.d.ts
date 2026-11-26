// Binary read for specs that load the real example photos (merges with node-test-shims.d.ts).
declare module 'node:fs' {
  export function readFileSync(path: string): Uint8Array;
}
