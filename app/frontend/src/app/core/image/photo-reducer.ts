import { ReducedPhoto } from '../model';
import { t } from '../../shared/texts.pl';

export const MAX_PHOTO_BYTES = 10 * 1024 * 1024;
export const MAX_EDGE_PX = 1568;
export const JPEG_QUALITY = 0.85;

export type PhotoErrorCode = 'INVALID_TYPE' | 'TOO_LARGE' | 'DECODE_FAILED';

const ERROR_TEXT = {
  INVALID_TYPE: 'form.photo.type',
  TOO_LARGE: 'form.photo.size',
  DECODE_FAILED: 'form.photo.decode',
} as const;

/** Rejection of `reducePhoto`; `message` is the Polish UI text (ADR-003 section 3.7). */
export class PhotoReduceError extends Error {
  constructor(readonly code: PhotoErrorCode) {
    super(t(ERROR_TEXT[code]));
    this.name = 'PhotoReduceError';
  }
}

const ALLOWED_MIME = new Set(['image/jpeg', 'image/png', 'image/webp']);
const ALLOWED_EXTENSIONS = new Set(['jpg', 'jpeg', 'png', 'webp']);

function hasAllowedType(file: File): boolean {
  const extension = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : '';
  return ALLOWED_MIME.has(file.type.toLowerCase()) && ALLOWED_EXTENSIONS.has(extension);
}

async function encodeJpeg(bitmap: ImageBitmap, width: number, height: number): Promise<Blob> {
  if (typeof OffscreenCanvas !== 'undefined') {
    const canvas = new OffscreenCanvas(width, height);
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('no 2d context');
    ctx.fillStyle = 'white';
    ctx.fillRect(0, 0, width, height);
    ctx.drawImage(bitmap, 0, 0, width, height);
    return canvas.convertToBlob({ type: 'image/jpeg', quality: JPEG_QUALITY });
  }
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('no 2d context');
  ctx.fillStyle = 'white';
  ctx.fillRect(0, 0, width, height);
  ctx.drawImage(bitmap, 0, 0, width, height);
  return new Promise<Blob>((resolve, reject) =>
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('toBlob failed'))),
      'image/jpeg',
      JPEG_QUALITY,
    ),
  );
}

/**
 * Validates the original file (AC-09/10), applies the EXIF orientation, scales the longest edge
 * to at most 1568 px (never upscales; the Jev triage model accepts 256-1568 px per side) and re-encodes as JPEG 0.85 (AC-15).
 */
export async function reducePhoto(file: File): Promise<ReducedPhoto> {
  if (!hasAllowedType(file)) throw new PhotoReduceError('INVALID_TYPE');
  if (file.size > MAX_PHOTO_BYTES) throw new PhotoReduceError('TOO_LARGE');

  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
  } catch {
    throw new PhotoReduceError('DECODE_FAILED');
  }
  try {
    const scale = Math.min(1, MAX_EDGE_PX / Math.max(bitmap.width, bitmap.height));
    const width = Math.max(1, Math.round(bitmap.width * scale));
    const height = Math.max(1, Math.round(bitmap.height * scale));
    const blob = await encodeJpeg(bitmap, width, height);
    return {
      blob,
      objectUrl: URL.createObjectURL(blob),
      originalName: file.name,
      width,
      height,
    };
  } catch {
    throw new PhotoReduceError('DECODE_FAILED');
  } finally {
    bitmap.close();
  }
}
