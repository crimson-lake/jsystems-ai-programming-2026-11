import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatButtonHarness } from '@angular/material/button/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { FakeCaseStore } from '../../../testing/fake-case-store';
import { CaseApiService } from '../../core/api/case-api.service';
import { CaseStore } from '../../core/state/case.store';
import { NewCaseService } from './new-case.service';
import texts from '../../shared/texts.pl.json';

@Component({ template: '' })
class Host {}

describe('NewCaseService (AC-44)', () => {
  let store: FakeCaseStore;
  let deleted: string[];
  let deleteResult: () => Observable<void>;
  let navigated: string[];
  let service: NewCaseService;
  let loader: HarnessLoader;

  beforeEach(() => {
    store = new FakeCaseStore();
    deleted = [];
    deleteResult = () => of(undefined);
    navigated = [];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: CaseStore, useValue: store },
        {
          provide: CaseApiService,
          useValue: {
            deleteCase: (id: string) => {
              deleted.push(id);
              return deleteResult();
            },
          },
        },
      ],
    });
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockImplementation((url) => {
      navigated.push(String(url));
      return Promise.resolve(true);
    });
    service = TestBed.inject(NewCaseService);
    loader = TestbedHarnessEnvironment.documentRootLoader(TestBed.createComponent(Host));
  });

  it('start() deletes the case, resets the store, then goes to the empty form', () => {
    service.start();
    expect(deleted).toEqual(['case-1']);
    expect(store.resetCount).toBe(1);
    expect(navigated).toEqual(['']);
  });

  it('start() still resets and navigates when DELETE fails', () => {
    deleteResult = () => throwError(() => new Error('404'));
    service.start();
    expect(store.resetCount).toBe(1);
    expect(navigated).toEqual(['']);
  });

  it('start() does not call DELETE without a case id', () => {
    store.caseId.set(null);
    service.start();
    expect(deleted).toEqual([]);
    expect(store.resetCount).toBe(1);
  });

  it('confirmAndStart() waits for the dialog: cancel changes nothing', async () => {
    service.confirmAndStart();
    const cancel = await loader.getHarness(MatButtonHarness.with({ text: texts['dialog.newCase.cancel'] }));
    await cancel.click();
    expect(deleted).toEqual([]);
    expect(store.resetCount).toBe(0);
    expect(navigated).toEqual([]);
  });

  it('confirmAndStart() starts the new case after "Nowa sprawa"', async () => {
    service.confirmAndStart();
    const confirm = await loader.getHarness(MatButtonHarness.with({ text: texts['dialog.newCase.confirm'] }));
    await confirm.click();
    // `afterClosed` fires once the dialog's exit animation has finished.
    await vi.waitFor(() => expect(deleted).toEqual(['case-1']));
    expect(store.resetCount).toBe(1);
    expect(navigated).toEqual(['']);
  });
});
