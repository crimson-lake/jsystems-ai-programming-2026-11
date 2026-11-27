import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { CaseStore } from '../state/case.store';
import { hasActiveCaseGuard } from './has-active-case.guard';

@Component({ template: 'form' })
class FormStub {}
@Component({ template: 'chat' })
class ChatStub {}

describe('hasActiveCaseGuard', () => {
  const caseId = signal<string | null>(null);

  async function navigate(url: string): Promise<{ router: Router; harness: RouterTestingHarness }> {
    TestBed.configureTestingModule({
      providers: [
        { provide: CaseStore, useValue: { caseId } },
        provideRouter([
          { path: '', component: FormStub },
          { path: 'sprawa', component: ChatStub, canActivate: [hasActiveCaseGuard] },
        ]),
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return { router: TestBed.inject(Router), harness };
  }

  it('redirects to the form when no case is in memory (AC-43 after reload)', async () => {
    caseId.set(null);
    const { router } = await navigate('/sprawa');
    expect(router.url).toBe('/');
  });

  it('lets the chat open while a case exists', async () => {
    caseId.set('case-1');
    const { router } = await navigate('/sprawa');
    expect(router.url).toBe('/sprawa');
  });
});
