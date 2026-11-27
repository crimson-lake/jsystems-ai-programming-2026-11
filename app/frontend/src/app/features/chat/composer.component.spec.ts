import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FakeCaseStore } from '../../../testing/fake-case-store';
import { CaseStore } from '../../core/state/case.store';
import texts from '../../shared/texts.pl.json';
import { ComposerComponent } from './composer.component';

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

describe('ComposerComponent (ADR-003 3.3, AC-31..AC-33, AC-38)', () => {
  let store: FakeCaseStore;
  let fixture: ComponentFixture<ComposerComponent>;
  let el: HTMLElement;
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
    store = new FakeCaseStore();
    TestBed.configureTestingModule({ providers: [{ provide: CaseStore, useValue: store }] });
    fixture = TestBed.createComponent(ComposerComponent);
    el = fixture.nativeElement;
    await fixture.whenStable();
  });
  afterEach(() => vi.unstubAllGlobals());

  const textarea = () => el.querySelector<HTMLTextAreaElement>('textarea')!;
  const fileInput = () => el.querySelector<HTMLInputElement>('input[type=file]')!;
  const button = (name: string) =>
    Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      (b.getAttribute('aria-label') ?? b.textContent ?? '').trim().includes(name),
    );
  const send = () => button(texts['chat.composer.send'])!;
  const attach = () => button(texts['chat.composer.attach'])!;
  const preview = () => el.querySelector('[data-testid=composer-photo]');
  const alert = () => el.querySelector('[role=alert]')?.textContent?.trim() ?? null;

  async function type(value: string): Promise<void> {
    textarea().value = value;
    textarea().dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function key(init: KeyboardEventInit): Promise<KeyboardEvent> {
    const event = new KeyboardEvent('keydown', { bubbles: true, cancelable: true, ...init });
    textarea().dispatchEvent(event);
    await fixture.whenStable();
    return event;
  }

  async function pick(file: File): Promise<void> {
    Object.defineProperty(fileInput(), 'files', { value: [file], configurable: true });
    fileInput().dispatchEvent(new Event('change'));
    await vi.waitFor(() => expect(preview() ?? alert()).toBeTruthy());
    await fixture.whenStable();
  }

  const jpeg = () => new File(['x'], 'laptop.jpg', { type: 'image/jpeg' });

  describe('text input', () => {
    it('has a visible label "Wiadomość" as the accessible name of a growing textarea', () => {
      expect(textarea().labels?.[0]?.textContent).toMatch(/Wiadomość/);
      expect(textarea().classList.contains('cdk-textarea-autosize')).toBe(true);
    });

    it('shows the counter n/2000', async () => {
      expect(el.textContent).toContain('0/2000');
      await type('abcde');
      expect(el.textContent).toContain('5/2000');
    });

    it('Enter sends the trimmed text and clears the input; the default newline is prevented', async () => {
      await type('  Jaką datę zakupu podałem?  ');
      const event = await key({ key: 'Enter' });
      expect(event.defaultPrevented).toBe(true);
      expect(store.sent).toEqual([{ text: 'Jaką datę zakupu podałem?', photo: undefined }]);
      expect(textarea().value).toBe('');
      expect(el.textContent).toContain('0/2000');
    });

    it('Shift+Enter neither sends nor prevents the newline', async () => {
      await type('linia');
      const event = await key({ key: 'Enter', shiftKey: true });
      expect(event.defaultPrevented).toBe(false);
      expect(store.sent).toEqual([]);
    });

    it('does not send on Enter during IME composition', async () => {
      await type('x');
      await key({ key: 'Enter', isComposing: true });
      expect(store.sent).toEqual([]);
    });

    it('the send button is named "Wyślij wiadomość" and sends on click', async () => {
      await type('Pytanie');
      expect(send().disabled).toBe(false);
      send().click();
      await fixture.whenStable();
      expect(store.sent).toHaveLength(1);
    });

    it.each(['', '   ', ' \n\t '])('blocks empty or whitespace-only text %j without a photo', async (value) => {
      await type(value);
      expect(send().disabled).toBe(true);
      await key({ key: 'Enter' });
      expect(store.sent).toEqual([]);
      expect(alert()).toBe(texts['chat.message.empty']);
    });

    it('clears the empty-message error when typing', async () => {
      await key({ key: 'Enter' });
      expect(alert()).toBe(texts['chat.message.empty']);
      await type('a');
      expect(alert()).toBeNull();
    });

    it('accepts exactly 2000 characters', async () => {
      await type('a'.repeat(2000));
      expect(send().disabled).toBe(false);
      expect(alert()).toBeNull();
      await key({ key: 'Enter' });
      expect(store.sent).toHaveLength(1);
    });

    it('blocks 2001 characters with the tooLong text, on the button and on Enter', async () => {
      await type('a'.repeat(2001));
      expect(send().disabled).toBe(true);
      expect(alert()).toBe(texts['chat.message.tooLong']);
      expect(el.textContent).toContain('2001/2000');
      await key({ key: 'Enter' });
      expect(store.sent).toEqual([]);
    });

    it('disables send while streaming (AC-38) and keeps the typed text', async () => {
      await type('Kolejne pytanie');
      store.streaming.set(true);
      await fixture.whenStable();
      expect(send().disabled).toBe(true);
      await key({ key: 'Enter' });
      expect(store.sent).toEqual([]);
      expect(textarea().value).toBe('Kolejne pytanie');
      store.streaming.set(false);
      await fixture.whenStable();
      expect(send().disabled).toBe(false);
    });
  });

  describe('photo', () => {
    it('the attach button "Dodaj zdjęcie" opens a hidden file input for jpeg/png/webp without capture', () => {
      const input = fileInput();
      expect(input.hidden).toBe(true);
      expect(input.accept).toBe('image/jpeg,image/png,image/webp');
      expect(input.hasAttribute('capture')).toBe(false);
      const click = vi.spyOn(input, 'click');
      attach().click();
      expect(click).toHaveBeenCalled();
    });

    it('shows a removable preview above the input after a valid pick', async () => {
      await pick(jpeg());
      const thumb = preview()!;
      expect(thumb).toBeTruthy();
      expect(thumb.compareDocumentPosition(textarea()) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      expect(el.textContent).toContain('laptop.jpg');
      button(texts['chat.composer.removePhoto'])!.click();
      await fixture.whenStable();
      expect(preview()).toBeNull();
      expect(revoke).toHaveBeenCalledWith('blob:fake-1');
    });

    it('allows a photo-only send (AC-32) and hands the photo to the store; the store owns its URL afterwards', async () => {
      await pick(jpeg());
      expect(send().disabled).toBe(false);
      send().click();
      await fixture.whenStable();
      expect(store.sent).toHaveLength(1);
      expect(store.sent[0].text).toBe('');
      expect(store.sent[0].photo?.objectUrl).toBe('blob:fake-1');
      expect(preview()).toBeNull();
      expect(revoke).not.toHaveBeenCalled();
    });

    it('sends text together with the photo', async () => {
      await pick(jpeg());
      await type('Zobacz');
      await key({ key: 'Enter' });
      expect(store.sent[0]).toMatchObject({ text: 'Zobacz' });
      expect(store.sent[0].photo).toBeDefined();
    });

    it('rejects a wrong type before upload with the form.photo.type text', async () => {
      await pick(new File(['x'], 'a.gif', { type: 'image/gif' }));
      expect(preview()).toBeNull();
      expect(alert()).toBe(texts['form.photo.type']);
    });

    it('rejects a file over 10 MB with the form.photo.size text', async () => {
      const big = new File(['x'], 'big.jpg', { type: 'image/jpeg' });
      Object.defineProperty(big, 'size', { value: 10 * 1024 * 1024 + 1 });
      await pick(big);
      expect(preview()).toBeNull();
      expect(alert()).toBe(texts['form.photo.size']);
    });

    it('replacing the photo revokes the previous preview URL', async () => {
      await pick(jpeg());
      await pick(new File(['y'], 'two.png', { type: 'image/png' }));
      expect(revoke).toHaveBeenCalledWith('blob:fake-1');
      expect(el.textContent).toContain('two.png');
    });
  });

  describe('photo limit (AC-33)', () => {
    it('keeps attach enabled below the limit and shows no hint', () => {
      store.additionalPhotoCount.set(2);
      expect(attach().disabled).toBe(false);
      expect(el.textContent).not.toContain(texts['chat.photoLimit']);
    });

    it('disables attach with the chat.photoLimit hint at 3 photos', async () => {
      store.additionalPhotoCount.set(3);
      await fixture.whenStable();
      expect(attach().disabled).toBe(true);
      expect(el.textContent).toContain(texts['chat.photoLimit']);
      expect(attach().getAttribute('aria-describedby')).toBeTruthy();
    });

    it('still lets the customer send text at the limit', async () => {
      store.additionalPhotoCount.set(3);
      await type('Tylko tekst');
      expect(send().disabled).toBe(false);
    });

    it('drops a staged preview when the limit is reached meanwhile', async () => {
      await pick(jpeg());
      store.additionalPhotoCount.set(3);
      await fixture.whenStable();
      expect(preview()).toBeNull();
      expect(revoke).toHaveBeenCalledWith('blob:fake-1');
    });
  });

  describe('server refusals (store.composerRejection)', () => {
    it('422: restores the text, removes the preview, the limit hint stays', async () => {
      await type('Opis');
      await key({ key: 'Enter' });
      expect(textarea().value).toBe('');
      store.additionalPhotoCount.set(3);
      store.composerRejection.set({ text: 'Opis', errorKey: 'chat.photoLimit' });
      await fixture.whenStable();
      expect(textarea().value).toBe('Opis');
      expect(preview()).toBeNull();
      expect(el.textContent).toContain(texts['chat.photoLimit']);
      expect(store.composerRejection()).toBeNull();
    });

    it('413: keeps the text, removes a staged preview and shows form.photo.size under the composer', async () => {
      await pick(jpeg());
      store.composerRejection.set({ text: 'Duże', errorKey: 'form.photo.size' });
      await fixture.whenStable();
      expect(textarea().value).toBe('Duże');
      expect(preview()).toBeNull();
      expect(alert()).toBe(texts['form.photo.size']);
      expect(store.composerRejection()).toBeNull();
    });

    it('400: shows the validation text under the composer', async () => {
      store.composerRejection.set({ text: '', errorKey: 'chat.message.empty' });
      await fixture.whenStable();
      expect(alert()).toBe(texts['chat.message.empty']);
    });

    it('does not overwrite text typed in the meantime', async () => {
      await type('nowy tekst');
      store.composerRejection.set({ text: 'stary', errorKey: 'form.photo.size' });
      await fixture.whenStable();
      expect(textarea().value).toBe('nowy tekst');
    });
  });
});
