package io.mint.ai.agent.model;

public record BackendStackSpec(
        String framework,
        String version,
        String orm,
        String javaVersion
) {
}
