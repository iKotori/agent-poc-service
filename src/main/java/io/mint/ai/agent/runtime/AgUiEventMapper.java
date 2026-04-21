package io.mint.ai.agent.runtime;

import io.mint.ai.agent.model.AgUiEventEnvelope;
import io.mint.ai.agent.model.GenerateResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class AgUiEventMapper {

    public AgUiEventEnvelope runStarted(RunContext context, String workspaceRoot, List<String> stages) {
        return envelope(context, "run.started", Map.of(
                "workspaceRoot", workspaceRoot,
                "stages", stages
        ));
    }

    public AgUiEventEnvelope stageStarted(RunContext context, String stage, int index, int total) {
        return envelope(context, "stage.started", Map.of(
                "stage", stage,
                "index", index,
                "total", total
        ));
    }

    public AgUiEventEnvelope stageCompleted(RunContext context, String stage, String artifact, long durationMs) {
        return envelope(context, "stage.completed", Map.of(
                "stage", stage,
                "artifact", artifact,
                "durationMs", durationMs
        ));
    }

    public AgUiEventEnvelope buildLog(RunContext context, String target, String line) {
        return envelope(context, "build.log", Map.of(
                "target", target,
                "line", line
        ));
    }

    public AgUiEventEnvelope runCompleted(RunContext context, GenerateResult result, String artifactsDir) {
        return envelope(context, "run.completed", Map.of(
                "result", result,
                "artifactsDir", artifactsDir
        ));
    }

    public AgUiEventEnvelope runError(RunContext context, String stage, String message) {
        return envelope(context, "run.error", Map.of(
                "code", "STAGE_FAILED",
                "stage", stage,
                "message", message == null ? "" : message
        ));
    }

    public AgUiEventEnvelope runCancelled(RunContext context) {
        return envelope(context, "run.cancelled", Map.of(
                "by", "user",
                "reason", "manual_cancel"
        ));
    }

    public AgUiEventEnvelope heartbeat(RunContext context, String status) {
        return envelope(context, "heartbeat", Map.of(
                "status", status
        ));
    }

    private AgUiEventEnvelope envelope(RunContext context, String type, Map<String, Object> payload) {
        String eventId = "evt-" + context.nextSequence();
        return new AgUiEventEnvelope(type, eventId, context.runId(), Instant.now(), payload);
    }
}
