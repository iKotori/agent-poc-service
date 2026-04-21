package io.mint.ai.agent.service;

import io.mint.ai.agent.model.*;
import io.mint.ai.agent.orchestrator.GenerateOrchestrator;
import io.mint.ai.agent.orchestrator.OrchestratorEventListener;
import io.mint.ai.agent.runtime.AgUiEventMapper;
import io.mint.ai.agent.runtime.RunContext;
import io.mint.ai.agent.runtime.RunEventBus;
import io.mint.ai.agent.runtime.RunRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

@Service
public class RunExecutionService {

    private final WorkspaceService workspaceService;
    private final GenerateOrchestrator generateOrchestrator;
    private final RunRegistry runRegistry;
    private final RunEventBus runEventBus;
    private final AgUiEventMapper agUiEventMapper;
    private final Executor runTaskExecutor;

    public RunExecutionService(WorkspaceService workspaceService,
                               GenerateOrchestrator generateOrchestrator,
                               RunRegistry runRegistry,
                               RunEventBus runEventBus,
                               AgUiEventMapper agUiEventMapper,
                               @Qualifier("runTaskExecutor") Executor runTaskExecutor) {
        this.workspaceService = workspaceService;
        this.generateOrchestrator = generateOrchestrator;
        this.runRegistry = runRegistry;
        this.runEventBus = runEventBus;
        this.agUiEventMapper = agUiEventMapper;
        this.runTaskExecutor = runTaskExecutor;
    }

    public CreateRunResponse startRun(CreateRunRequest request) {
        if (request == null
                || !StringUtils.hasText(request.userRequirement())
                || !StringUtils.hasText(request.workspaceRoot())) {
            throw new IllegalArgumentException("userRequirement and workspaceRoot are required");
        }

        String runId = UUID.randomUUID().toString();
        runRegistry.create(runId, request.userRequirement(), request.workspaceRoot());

        runTaskExecutor.execute(() -> executeRun(runId, request));

        return new CreateRunResponse(
                runId,
                RunState.ACCEPTED.name().toLowerCase(),
                Instant.now(),
                "/api/agent/runs/" + runId + "/events",
                "/api/agent/runs/" + runId + "/cancel"
        );
    }

    public boolean cancelRun(String runId) {
        RunContext context = runRegistry.get(runId).orElse(null);
        if (context == null) {
            return false;
        }
        return runRegistry.requestCancel(runId);
    }

    public boolean exists(String runId) {
        return runRegistry.get(runId).isPresent();
    }

    public RunStatusResponse getStatus(String runId) {
        RunContext context = runRegistry.get(runId).orElse(null);
        if (context == null) {
            return null;
        }

        return new RunStatusResponse(
                context.runId(),
                context.state().name().toLowerCase(),
                context.currentStage(),
                context.createdAt(),
                context.updatedAt()
        );
    }

    public RunHistoryResponse listRuns(String status, String keyword, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        long total = runRegistry.count(status, keyword);

        List<RunHistoryItemResponse> items = runRegistry.list(status, keyword, safePage, safeSize).stream()
                .map(context -> new RunHistoryItemResponse(
                        context.runId(),
                        context.state().name().toLowerCase(),
                        context.currentStage(),
                        abbreviate(context.userRequirement(), 120),
                        context.workspaceRoot(),
                        context.createdAt(),
                        context.updatedAt()
                ))
                .toList();

        return new RunHistoryResponse(total, safePage, safeSize, items);
    }

    private void executeRun(String runId, CreateRunRequest request) {
        RunContext context = runRegistry.get(runId)
                .orElseThrow(() -> new IllegalStateException("Run context not found: " + runId));

        try {
            String workspaceRoot = workspaceService.prepareWorkspace(runId, request.workspaceRoot());
            context.updateWorkspaceRoot(workspaceRoot);
            GenerateRequest actualRequest = new GenerateRequest(request.userRequirement(), workspaceRoot);

            generateOrchestrator.generateStreaming(
                    actualRequest,
                    listener(context),
                    context::isCancelRequested
            );
        } catch (CancellationException cancelled) {
            runRegistry.markCancelled(runId);
            runEventBus.publish(runId, agUiEventMapper.runCancelled(context));
        } catch (Exception ex) {
            if (context.state() == RunState.FAILED) {
                return;
            }

            runRegistry.markFailed(runId);
            if (context.state() == RunState.CANCELLED || context.state() == RunState.CANCELLING) {
                runEventBus.publish(runId, agUiEventMapper.runCancelled(context));
            } else {
                runEventBus.publish(runId, agUiEventMapper.runError(context, context.currentStage(), ex.getMessage()));
            }
        } finally {
            runEventBus.complete(runId);
        }
    }

    private String abbreviate(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private OrchestratorEventListener listener(RunContext context) {
        return new OrchestratorEventListener() {
            @Override
            public void onRunStarted(String workspaceRoot, java.util.List<String> stages) {
                runRegistry.markRunning(context.runId());
                runEventBus.publish(context.runId(), agUiEventMapper.runStarted(context, workspaceRoot, stages));
            }

            @Override
            public void onStageStarted(String stage, int index, int total) {
                runRegistry.markStage(context.runId(), stage);
                runEventBus.publish(context.runId(), agUiEventMapper.stageStarted(context, stage, index, total));
            }

            @Override
            public void onStageCompleted(String stage, String artifact, long durationMs) {
                runEventBus.publish(context.runId(), agUiEventMapper.stageCompleted(context, stage, artifact, durationMs));
            }

            @Override
            public void onBuildLog(String target, String line) {
                runEventBus.publish(context.runId(), agUiEventMapper.buildLog(context, target, line));
            }

            @Override
            public void onRunCompleted(GenerateResult result, String artifactsDir) {
                runRegistry.markCompleted(context.runId());
                runEventBus.publish(context.runId(), agUiEventMapper.runCompleted(context, result, artifactsDir));
            }

            @Override
            public void onRunError(String stage, Exception exception) {
                runRegistry.markFailed(context.runId());
                runEventBus.publish(context.runId(), agUiEventMapper.runError(context, stage, exception.getMessage()));
            }

            @Override
            public void onRunCancelled() {
                runRegistry.markCancelled(context.runId());
                runEventBus.publish(context.runId(), agUiEventMapper.runCancelled(context));
            }
        };
    }
}
