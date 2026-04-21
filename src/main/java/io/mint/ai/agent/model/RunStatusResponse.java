package io.mint.ai.agent.model;

import java.time.Instant;

public record RunStatusResponse(
        String runId,
        String status,
        String currentStage,
        Instant createdAt,
        Instant updatedAt
) {
}
