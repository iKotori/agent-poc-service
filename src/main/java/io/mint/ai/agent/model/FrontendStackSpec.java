package io.mint.ai.agent.model;

public record FrontendStackSpec(
        String framework,
        String language,
        String ui,
        String router,
        String state,
        String buildTool
) {
}
