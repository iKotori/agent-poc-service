package io.mint.ai.agent.model;

import java.time.Instant;

public record RunHistoryItemResponse(
        String runId,
        String status,
        String currentStage,
        String userRequirementPreview,
        String workspaceRoot,
        Instant createdAt,
        Instant updatedAt
) {
}
