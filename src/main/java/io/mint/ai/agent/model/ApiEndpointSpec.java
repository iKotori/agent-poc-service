package io.mint.ai.agent.model;

public record ApiEndpointSpec(
        String name,
        String method,
        String path,
        String requestType,
        String responseType,
        String description
) {
}
