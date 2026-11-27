package com.hsdc.copilot.llm;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cooperative cancellation handle shared between a turn and the model stream. */
public final class CancelToken {

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /** Cancels once; later calls are no-ops. Listeners run on the calling thread. */
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            listeners.forEach(Runnable::run);
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    /** Registers a listener; runs it immediately when the token is already cancelled. */
    public void onCancel(Runnable listener) {
        listeners.add(listener);
        if (cancelled.get()) {
            listener.run();
        }
    }
}
