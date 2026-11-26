import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { t } from './shared/texts.pl';

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  protected readonly title = t('app.title');
  protected readonly subtitle = t('app.subtitle');
}
