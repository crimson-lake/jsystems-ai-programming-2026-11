import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { MAX_PHOTO_BYTES, PhotoReduceError, reducePhoto } from './photo-reducer';
import texts from '../../shared/texts.pl.json';

// Real example photos (assets/example-images/README.md); dimensions from that README.
const IMAGES = join(process.cwd(), '..', '..', 'assets', 'example-images');
function example(name: string, type: string): File {
  return new File([readFileSync(join(IMAGES, name)) as BlobPart], name, { type });
}

interface FakeBitmap {
  width: number;
  height: number;
  close: () => void;
}

class FakeOffscreenCanvas {
  static instances: FakeOffscreenCanvas[] = [];
  readonly draws: { w: number; h: number }[] = [];
  constructor(
    public width: number,
    public height: number,
  ) {
    FakeOffscreenCanvas.instances.push(this);
  }
  getContext() {
    return {
      fillStyle: '',
      fillRect: () => undefined,
      drawImage: (_b: unknown, _x: number, _y: number, w: number, h: number) =>
        this.draws.push({ w, h }),
    };
  }
  convertToBlob(opts: { type: string; quality: number }) {
    FakeOffscreenCanvas.lastOptions = opts;
    return Promise.resolve(new Blob(['jpeg'], { type: opts.type }));
  }
  static lastOptions: { type: string; quality: number } | undefined;
}

function stubBitmap(width: number, height: number) {
  const bitmap: FakeBitmap = { width, height, close: vi.fn() };
  const create = vi.fn().mockResolvedValue(bitmap);
  vi.stubGlobal('createImageBitmap', create);
  return { bitmap, create };
}

describe('reducePhoto', () => {
  beforeEach(() => {
    FakeOffscreenCanvas.instances = [];
    FakeOffscreenCanvas.lastOptions = undefined;
    vi.stubGlobal('OffscreenCanvas', FakeOffscreenCanvas);
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:fake') }));
  });
  afterEach(() => vi.unstubAllGlobals());

  async function codeOf(file: File): Promise<string | undefined> {
    try {
      await reducePhoto(file);
    } catch (e) {
      return e instanceof PhotoReduceError ? e.code : 'OTHER';
    }
    return undefined;
  }

  it('reduces laptop-1.png (3024x3135, JPEG content) to a 1543x1600 JPEG', async () => {
    const { create } = stubBitmap(3024, 3135);
    const file = example('laptop-1.png', 'image/png');
    const reduced = await reducePhoto(file);
    expect(create).toHaveBeenCalledWith(file, { imageOrientation: 'from-image' });
    expect(reduced.width).toBe(1543);
    expect(reduced.height).toBe(1600);
    expect(reduced.blob.type).toBe('image/jpeg');
    expect(reduced.objectUrl).toBe('blob:fake');
    expect(reduced.originalName).toBe('laptop-1.png');
    expect(FakeOffscreenCanvas.lastOptions).toEqual({ type: 'image/jpeg', quality: 0.85 });
  });

  it('scales a landscape photo by the width and closes the bitmap', async () => {
    const { bitmap } = stubBitmap(3200, 1600);
    const reduced = await reducePhoto(example('phone-1.jpg', 'image/jpeg'));
    expect([reduced.width, reduced.height]).toEqual([1600, 800]);
    expect(bitmap.close).toHaveBeenCalled();
  });

  it('never upscales a small photo (phone-2.jpeg, 387x516)', async () => {
    stubBitmap(387, 516);
    const reduced = await reducePhoto(example('phone-2.jpeg', 'image/jpeg'));
    expect([reduced.width, reduced.height]).toEqual([387, 516]);
    expect(FakeOffscreenCanvas.instances[0].draws[0]).toEqual({ w: 387, h: 516 });
  });

  it('accepts WebP (laptop-2.webp, 863x843)', async () => {
    stubBitmap(863, 843);
    const reduced = await reducePhoto(example('laptop-2.webp', 'image/webp'));
    expect([reduced.width, reduced.height]).toEqual([863, 843]);
    expect(reduced.originalName).toBe('laptop-2.webp');
  });

  it('falls back to a <canvas> when OffscreenCanvas is missing', async () => {
    vi.stubGlobal('OffscreenCanvas', undefined);
    stubBitmap(2000, 1000);
    const drawImage = vi.fn();
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      fillStyle: '',
      fillRect: vi.fn(),
      drawImage,
    } as unknown as CanvasRenderingContext2D);
    const toBlob = vi
      .spyOn(HTMLCanvasElement.prototype, 'toBlob')
      .mockImplementation((cb, type, quality) => {
        expect([type, quality]).toEqual(['image/jpeg', 0.85]);
        cb(new Blob(['jpeg'], { type: 'image/jpeg' }));
      });
    const reduced = await reducePhoto(example('phone-1.jpg', 'image/jpeg'));
    expect([reduced.width, reduced.height]).toEqual([1600, 800]);
    expect(drawImage).toHaveBeenCalled();
    expect(toBlob).toHaveBeenCalled();
  });

  it('rejects GIF and HEIC (byte-level fixtures) with INVALID_TYPE', async () => {
    stubBitmap(10, 10);
    const gif = new File([new Uint8Array([0x47, 0x49, 0x46, 0x38, 0x39, 0x61])], 'a.gif', {
      type: 'image/gif',
    });
    const heic = new File(
      [new Uint8Array([0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69, 0x63])],
      'IMG_1.heic',
      { type: 'image/heic' },
    );
    expect(await codeOf(gif)).toBe('INVALID_TYPE');
    expect(await codeOf(heic)).toBe('INVALID_TYPE');
  });

  it('rejects when MIME and extension disagree about being an allowed type', async () => {
    stubBitmap(10, 10);
    expect(await codeOf(new File(['x'], 'a.jpg', { type: 'image/gif' }))).toBe('INVALID_TYPE');
    expect(await codeOf(new File(['x'], 'a.gif', { type: 'image/jpeg' }))).toBe('INVALID_TYPE');
    expect(await codeOf(new File(['x'], 'noext', { type: 'image/png' }))).toBe('INVALID_TYPE');
  });

  it('rejects a file larger than 10 MB with TOO_LARGE, accepts exactly 10 MB', async () => {
    stubBitmap(100, 100);
    const big = new File([new Uint8Array(MAX_PHOTO_BYTES + 1)], 'big.jpg', { type: 'image/jpeg' });
    expect(await codeOf(big)).toBe('TOO_LARGE');
    const edge = new File([new Uint8Array(MAX_PHOTO_BYTES)], 'edge.jpg', { type: 'image/jpeg' });
    expect(await codeOf(edge)).toBeUndefined();
  });

  it('rejects an undecodable file with DECODE_FAILED', async () => {
    vi.stubGlobal('createImageBitmap', vi.fn().mockRejectedValue(new Error('bad')));
    expect(await codeOf(new File(['x'], 'broken.jpg', { type: 'image/jpeg' }))).toBe(
      'DECODE_FAILED',
    );
  });

  it('maps error codes to the section 3.7 texts', () => {
    expect(new PhotoReduceError('INVALID_TYPE').message).toBe(texts['form.photo.type']);
    expect(new PhotoReduceError('TOO_LARGE').message).toBe(texts['form.photo.size']);
    expect(new PhotoReduceError('DECODE_FAILED').message).toBe(texts['form.photo.decode']);
  });
});
