package io.mint.ai.agent.runtime;

import io.mint.ai.agent.model.RunState;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class RunContext {

    private final String runId;
    private final Instant createdAt;
    private final AtomicLong sequence;
    private final AtomicBoolean cancelRequested;
    private final String userRequirement;
    private volatile RunState state;
    private volatile String currentStage;
    private volatile String workspaceRoot;
    private volatile Instant updatedAt;

    public RunContext(String runId, String userRequirement, String workspaceRoot) {
        this.runId = runId;
        this.userRequirement = userRequirement;
        this.workspaceRoot = workspaceRoot;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.state = RunState.ACCEPTED;
        this.sequence = new AtomicLong(0);
        this.cancelRequested = new AtomicBoolean(false);
    }

    public String runId() {
        return runId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public RunState state() {
        return state;
    }

    public String currentStage() {
        return currentStage;
    }

    public boolean isCancelRequested() {
        return cancelRequested.get();
    }

    public String userRequirement() {
        return userRequirement;
    }

    public String workspaceRoot() {
        return workspaceRoot;
    }

    public synchronized void updateWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
        this.updatedAt = Instant.now();
    }

    public long nextSequence() {
        return sequence.incrementAndGet();
    }

    public synchronized void markCancelRequested() {
        if (isTerminal()) {
            return;
        }
        cancelRequested.set(true);
        this.state = RunState.CANCELLING;
        this.updatedAt = Instant.now();
    }

    public synchronized void markRunning() {
        if (isTerminal()) {
            return;
        }
        if (cancelRequested.get()) {
            this.state = RunState.CANCELLING;
            this.updatedAt = Instant.now();
            return;
        }
        this.state = RunState.RUNNING;
        this.updatedAt = Instant.now();
    }

    public synchronized void markStage(String stage) {
        if (isTerminal()) {
            return;
        }
        this.currentStage = stage;
        this.updatedAt = Instant.now();
    }

    public synchronized void markCompleted() {
        if (isTerminal()) {
            return;
        }
        if (cancelRequested.get()) {
            this.state = RunState.CANCELLED;
            this.updatedAt = Instant.now();
            return;
        }
        this.state = RunState.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public synchronized void markFailed() {
        if (isTerminal()) {
            return;
        }
        if (cancelRequested.get()) {
            this.state = RunState.CANCELLED;
            this.updatedAt = Instant.now();
            return;
        }
        this.state = RunState.FAILED;
        this.updatedAt = Instant.now();
    }

    public synchronized void markCancelled() {
        if (isTerminal()) {
            return;
        }
        this.state = RunState.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public boolean isTerminal() {
        return state == RunState.COMPLETED
                || state == RunState.FAILED
                || state == RunState.CANCELLED;
    }
}
