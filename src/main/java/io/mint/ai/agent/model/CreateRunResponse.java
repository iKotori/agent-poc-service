package io.mint.ai.agent.model;

import java.time.Instant;

public record CreateRunResponse(
        String runId,
        String status,
        Instant createdAt,
        String streamUrl,
        String cancelUrl
) {
}
