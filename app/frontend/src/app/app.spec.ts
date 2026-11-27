import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import texts from './shared/texts.pl.json';

describe('App shell', () => {
  async function render(): Promise<HTMLElement> {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the product title and subtitle from texts.pl.json', async () => {
    const el = await render();
    const header = el.querySelector('header')!;
    expect(header.querySelector('.app-header__title')?.textContent?.trim()).toBe(
      texts['app.title'],
    );
    expect(header.querySelector('.app-header__subtitle')?.textContent?.trim()).toBe(
      texts['app.subtitle'],
    );
  });

  it('shows the logo at 178x45 without upscaling', async () => {
    const el = await render();
    const img = el.querySelector('header img')!;
    expect(img.getAttribute('src')).toBe('logo.webp');
    expect(img.getAttribute('width')).toBe('178');
    expect(img.getAttribute('height')).toBe('45');
  });

  it('renders a main landmark with the router outlet', async () => {
    const el = await render();
    expect(el.querySelector('main router-outlet')).toBeTruthy();
  });
});
