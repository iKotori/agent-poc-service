package io.mint.ai.agent.model;

import java.time.Instant;
import java.util.Map;

public record AgUiEventEnvelope(
        String type,
        String eventId,
        String runId,
        Instant timestamp,
        Map<String, Object> payload
) {
}
