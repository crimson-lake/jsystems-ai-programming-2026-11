package com.hsdc.copilot.cases;

import com.hsdc.copilot.analysis.ImageAnalysis;
import com.hsdc.copilot.decision.Decision;
import com.hsdc.copilot.triage.TriageResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One customer case (ADR-002 section 4). Mutable: every field except {@code lock} and
 * {@code deleted} is written only while holding {@link #getLock()}. {@code messages} and
 * {@code committedClientMessageIds} are also safe to read without the lock, because the pre-stream replay check
 * (ADR-002 section 3.4, step 3) runs before the busy check and may overlap a running turn's commit.
 */
public class CaseSession {

    private final UUID caseId;
    private final CaseForm form;
    private final Instant createdAt;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean deleted;

    private TriageResult triage;
    private final List<ImageAnalysis> analyses = new ArrayList<>();
    private final List<ChatMessage> messages = new CopyOnWriteArrayList<>();
    private final Set<UUID> committedClientMessageIds = ConcurrentHashMap.newKeySet();
    private Decision currentDecision;
    private int additionalPhotoCount;

    public CaseSession(UUID caseId, CaseForm form, Instant createdAt) {
        this.caseId = caseId;
        this.form = form;
        this.createdAt = createdAt;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public CaseForm getForm() {
        return form;
    }

    public CaseType getCaseType() {
        return form.caseType();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    /** Triage result of the first photo; empty when triage is off or unavailable. */
    public Optional<TriageResult> getTriage() {
        return Optional.ofNullable(triage);
    }

    public void setTriage(TriageResult triage) {
        this.triage = triage;
    }

    public List<ImageAnalysis> getAnalyses() {
        return analyses;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public Set<UUID> getCommittedClientMessageIds() {
        return committedClientMessageIds;
    }

    /** Null until the first decision is committed. */
    public Decision getCurrentDecision() {
        return currentDecision;
    }

    public void setCurrentDecision(Decision currentDecision) {
        this.currentDecision = currentDecision;
    }

    public int getAdditionalPhotoCount() {
        return additionalPhotoCount;
    }

    public void setAdditionalPhotoCount(int additionalPhotoCount) {
        this.additionalPhotoCount = additionalPhotoCount;
    }
}
