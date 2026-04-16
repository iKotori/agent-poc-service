package io.mint.ai.agent.model;

import java.util.List;

public record RequirementSpec(
        String moduleName,
        String moduleLabel,
        String bizSummary,
        List<EntitySpec> entities,
        List<PageSpec> pages,
        List<String> operations,
        List<String> assumptions
) {
}