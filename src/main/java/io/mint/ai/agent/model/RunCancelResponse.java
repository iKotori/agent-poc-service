package io.mint.ai.agent.model;

public record RunCancelResponse(
        String runId,
        String status
) {
}
