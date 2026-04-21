package io.mint.ai.agent.model;

public record CreateRunRequest(
        String userRequirement,
        String workspaceRoot
) {
}
