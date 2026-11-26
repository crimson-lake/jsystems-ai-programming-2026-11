package com.hsdc.copilot.cases;

import java.util.Optional;
import java.util.UUID;

/** Port for the in-memory case store (implemented in T24). */
public interface CaseStore {

    Optional<CaseSession> find(UUID caseId);

    void save(CaseSession session);

    void remove(UUID caseId);
}
