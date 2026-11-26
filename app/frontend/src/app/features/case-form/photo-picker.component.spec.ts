import { TestBed, ComponentFixture } from '@angular/core/testing';
import { PhotoPickerComponent, PickedPhoto } from './photo-picker.component';
import { CaseType } from '../../core/model';
import texts from '../../shared/texts.pl.json';

class FakeOffscreenCanvas {
  constructor(
    public width: number,
    public height: number,
  ) {}
  getContext() {
    return { fillStyle: '', fillRect: () => undefined, drawImage: () => undefined };
  }
  convertToBlob(opts: { type: string }) {
    return Promise.resolve(new Blob(['jpeg'], { type: opts.type }));
  }
}

function pick(fixture: ComponentFixture<PhotoPickerComponent>, file: File): Promise<void> {
  const input = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>(
    'input[type=file]',
  )!;
  Object.defineProperty(input, 'files', { value: [file], configurable: true });
  input.dispatchEvent(new Event('change'));
  return fixture.whenStable();
}

describe('PhotoPickerComponent', () => {
  let fixture: ComponentFixture<PhotoPickerComponent>;
  let el: HTMLElement;
  let emitted: (PickedPhoto | null)[];
  let revoke: ReturnType<typeof vi.fn>;
  let counter: number;

  beforeEach(async () => {
    counter = 0;
    revoke = vi.fn();
    vi.stubGlobal('OffscreenCanvas', FakeOffscreenCanvas);
    vi.stubGlobal(
      'createImageBitmap',
      vi.fn().mockResolvedValue({ width: 2000, height: 1000, close: () => undefined }),
    );
    vi.stubGlobal(
      'URL',
      Object.assign(URL, {
        createObjectURL: () => `blob:fake-${++counter}`,
        revokeObjectURL: revoke,
      }),
    );
    await TestBed.configureTestingModule({ imports: [PhotoPickerComponent] }).compileComponents();
    fixture = TestBed.createComponent(PhotoPickerComponent);
    emitted = [];
    fixture.componentInstance.photoChange.subscribe((p) => emitted.push(p));
    el = fixture.nativeElement;
    await fixture.whenStable();
  });
  afterEach(() => vi.unstubAllGlobals());

  const fileInput = () => el.querySelector<HTMLInputElement>('input[type=file]')!;
  const text = () => el.textContent ?? '';
  const button = (label: string) =>
    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === label)!;

  it('has a hidden file input for JPEG/PNG/WebP without capture', () => {
    expect(fileInput().getAttribute('accept')).toBe('image/jpeg,image/png,image/webp');
    expect(fileInput().hasAttribute('capture')).toBe(false);
    expect(fileInput().hidden).toBe(true);
    expect(fileInput().multiple).toBe(false);
  });

  it('the "Wybierz zdjęcie" button opens the chooser', () => {
    const click = vi.spyOn(fileInput(), 'click');
    button(texts['form.photo.button']).click();
    expect(click).toHaveBeenCalled();
  });

  it.each([
    [null, 'form.photo.hint.none'],
    ['RETURN', 'form.photo.hint.return'],
    ['COMPLAINT', 'form.photo.hint.complaint'],
  ] as [
    CaseType | null,
    'form.photo.hint.none' | 'form.photo.hint.return' | 'form.photo.hint.complaint',
  ][])('shows the hint for %s without the AC reference', async (type, key) => {
    fixture.componentRef.setInput('caseType', type);
    await fixture.whenStable();
    expect(text()).toContain(texts[key].replace(/\s*\(AC-12\)/, ''));
    expect(text()).not.toContain('AC-12');
  });

  it('shows thumbnail, file name and Usuń after a valid pick, and emits the reduced photo', async () => {
    const file = new File(['x'], 'phone-1.jpg', { type: 'image/jpeg' });
    await pick(fixture, file);
    const img = el.querySelector<HTMLImageElement>('[data-testid="photo-thumbnail"]')!;
    expect(img.getAttribute('src')).toBe('blob:fake-1');
    expect(text()).toContain('phone-1.jpg');
    expect(button(texts['form.photo.remove'])).toBeTruthy();
    expect(emitted).toHaveLength(1);
    expect(emitted[0]!.file).toBe(file);
    expect(emitted[0]!.reduced.width).toBe(1568);
  });

  it('replaces the photo on a new pick and revokes the old object URL (AC-08)', async () => {
    await pick(fixture, new File(['x'], 'a.jpg', { type: 'image/jpeg' }));
    await pick(fixture, new File(['x'], 'b.png', { type: 'image/png' }));
    expect(text()).toContain('b.png');
    expect(text()).not.toContain('a.jpg');
    expect(revoke).toHaveBeenCalledWith('blob:fake-1');
    expect(emitted).toHaveLength(2);
  });

  it('removes the photo with Usuń, emits null and revokes the URL', async () => {
    await pick(fixture, new File(['x'], 'a.jpg', { type: 'image/jpeg' }));
    button(texts['form.photo.remove']).click();
    await fixture.whenStable();
    expect(el.querySelector('[data-testid="photo-thumbnail"]')).toBeNull();
    expect(emitted.at(-1)).toBeNull();
    expect(revoke).toHaveBeenCalledWith('blob:fake-1');
  });

  it('rejects a GIF with the type text and emits nothing', async () => {
    await pick(fixture, new File(['x'], 'a.gif', { type: 'image/gif' }));
    expect(text()).toContain(texts['form.photo.type']);
    expect(el.querySelector('[data-testid="photo-thumbnail"]')).toBeNull();
    expect(emitted).toHaveLength(0);
  });

  it('rejects a file over 10 MB with the size text', async () => {
    const big = new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'big.jpg', {
      type: 'image/jpeg',
    });
    await pick(fixture, big);
    expect(text()).toContain(texts['form.photo.size']);
  });

  it('shows the decode text for an unreadable file', async () => {
    vi.stubGlobal('createImageBitmap', vi.fn().mockRejectedValue(new Error('bad')));
    await pick(fixture, new File(['x'], 'a.jpg', { type: 'image/jpeg' }));
    expect(text()).toContain(texts['form.photo.decode']);
  });

  it('shows the required text when asked to', async () => {
    fixture.componentRef.setInput('showRequired', true);
    await fixture.whenStable();
    expect(text()).toContain(texts['form.photo.required']);
  });

  it('disables the buttons while the form is submitting', async () => {
    await pick(fixture, new File(['x'], 'a.jpg', { type: 'image/jpeg' }));
    fixture.componentRef.setInput('disabled', true);
    await fixture.whenStable();
    expect(Array.from(el.querySelectorAll('button')).every((b) => b.disabled)).toBe(true);
  });
});
