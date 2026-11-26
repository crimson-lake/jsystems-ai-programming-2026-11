import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FakeCaseStore, decisionView, uiMessage } from '../../../testing/fake-case-store';
import { CaseStore } from '../../core/state/case.store';
import { MessageListComponent } from './message-list.component';
import { NewCaseService } from './new-case.service';

describe('MessageListComponent', () => {
  let store: FakeCaseStore;
  let fixture: ComponentFixture<MessageListComponent>;
  let el: HTMLElement;

  beforeEach(async () => {
    store = new FakeCaseStore();
    TestBed.configureTestingModule({
      providers: [
        { provide: CaseStore, useValue: store },
        { provide: NewCaseService, useValue: { start: () => undefined } },
      ],
    });
    fixture = TestBed.createComponent(MessageListComponent);
    el = fixture.nativeElement;
    await fixture.whenStable();
  });

  const user = (id: string, text: string) =>
    uiMessage({ id, role: 'user', text, isFirst: false, status: 'complete' });
  const assistant = (id: string, text: string, status: 'streaming' | 'complete' = 'complete') =>
    uiMessage({ id, text, status, isFirst: id === 'a1', decision: decisionView() });
  const typing = () => el.querySelector('app-typing-indicator');

  it('renders messages in store order, by role', async () => {
    store.messages.set([assistant('a1', 'Pierwsza'), user('u1', 'Pytanie'), assistant('a2', 'Druga')]);
    await fixture.whenStable();
    const kinds = Array.from(el.querySelectorAll('app-assistant-message, app-user-message')).map((e) =>
      e.tagName.toLowerCase(),
    );
    expect(kinds).toEqual(['app-assistant-message', 'app-user-message', 'app-assistant-message']);
    expect(el.querySelectorAll('[data-testid=assistant-message]').length).toBe(2);
  });

  it('has a labelled scroll container', () => {
    const list = el.querySelector('.message-list')!;
    expect(list.getAttribute('aria-label')).toBeTruthy();
  });

  it('shows the typing indicator from send until the first delta (AC-37)', async () => {
    store.messages.set([assistant('a1', 'Pierwsza'), user('u2', 'Pytanie')]);
    store.streaming.set(true);
    await fixture.whenStable();
    expect(typing()).toBeTruthy();

    // `decision` arrived: assistant shell without text yet.
    store.messages.set([assistant('a1', 'Pierwsza'), user('u2', 'Pytanie'), assistant('a3', '', 'streaming')]);
    await fixture.whenStable();
    expect(typing()).toBeTruthy();

    // first delta
    store.messages.set([assistant('a1', 'Pierwsza'), user('u2', 'Pytanie'), assistant('a3', 'Od', 'streaming')]);
    await fixture.whenStable();
    expect(typing()).toBeNull();
  });

  it('shows no typing indicator when nothing is streaming', async () => {
    store.messages.set([assistant('a1', 'Pierwsza'), user('u2', 'Pytanie')]);
    store.streaming.set(false);
    await fixture.whenStable();
    expect(typing()).toBeNull();
  });
});
