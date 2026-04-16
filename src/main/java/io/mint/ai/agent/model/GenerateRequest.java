package io.mint.ai.agent.model;

public record GenerateRequest(String userRequirement,
                              String workspaceRoot) {
}
