package io.mint.ai.agent.runtime;

import io.mint.ai.agent.model.RunState;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RunRegistry {

    private final Map<String, RunContext> runs = new ConcurrentHashMap<>();

    public RunContext create(String runId, String userRequirement, String workspaceRoot) {
        RunContext context = new RunContext(runId, userRequirement, workspaceRoot);
        runs.put(runId, context);
        return context;
    }

    public Optional<RunContext> get(String runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    public List<RunContext> list(String status, String keyword, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int start = (safePage - 1) * safeSize;

        return runs.values().stream()
                .filter(context -> filterByStatus(context, status))
                .filter(context -> filterByKeyword(context, keyword))
                .sorted(Comparator.comparing(RunContext::createdAt).reversed())
                .skip(start)
                .limit(safeSize)
                .toList();
    }

    public long count(String status, String keyword) {
        return runs.values().stream()
                .filter(context -> filterByStatus(context, status))
                .filter(context -> filterByKeyword(context, keyword))
                .count();
    }

    public boolean requestCancel(String runId) {
        RunContext context = runs.get(runId);
        if (context == null) {
            return false;
        }

        synchronized (context) {
            if (context.isTerminal()) {
                return false;
            }
            if (context.state() == RunState.CANCELLING) {
                return true;
            }
            context.markCancelRequested();
            return true;
        }
    }

    public void markRunning(String runId) {
        RunContext context = runs.get(runId);
        if (context != null) {
            context.markRunning();
        }
    }

    public void markStage(String runId, String stage) {
        RunContext context = runs.get(runId);
        if (context != null) {
            context.markStage(stage);
        }
    }

    public void markCompleted(String runId) {
        RunContext context = runs.get(runId);
        if (context != null) {
            context.markCompleted();
        }
    }

    public void markFailed(String runId) {
        RunContext context = runs.get(runId);
        if (context != null) {
            context.markFailed();
        }
    }

    public void markCancelled(String runId) {
        RunContext context = runs.get(runId);
        if (context != null) {
            context.markCancelled();
        }
    }

    private boolean filterByStatus(RunContext context, String status) {
        if (!StringUtils.hasText(status)) {
            return true;
        }
        return context.state().name().equalsIgnoreCase(status);
    }

    private boolean filterByKeyword(RunContext context, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String lowerKeyword = keyword.toLowerCase();
        return context.runId().toLowerCase().contains(lowerKeyword)
                || (context.userRequirement() != null && context.userRequirement().toLowerCase().contains(lowerKeyword))
                || (context.workspaceRoot() != null && context.workspaceRoot().toLowerCase().contains(lowerKeyword));
    }
}
