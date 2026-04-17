package io.mint.ai.agent.model;

import java.util.List;

public record RequirementSpec(
        String moduleName,
        String moduleLabel,
        String bizSummary,
        FrontendStackSpec frontendStack,
        BackendStackSpec backendStack,
        List<EntitySpec> entities,
        List<PageSpec> pages,
        List<String> operations,
        List<String> tableColumns,
        List<String> formFields,
        List<String> detailFields,
        List<String> assumptions
) {
}