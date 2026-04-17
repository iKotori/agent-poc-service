package io.mint.ai.agent.model;

public record GenerateResult(
        RequirementSpec requirementSpec,
        SqlSpec sqlSpec,
        ApiSpec apiSpec,
        GenerationWriteSummary backendSummary,
        GenerationWriteSummary frontendSummary,
        String backendBuildLog,
        String frontendBuildLog
) {
}
