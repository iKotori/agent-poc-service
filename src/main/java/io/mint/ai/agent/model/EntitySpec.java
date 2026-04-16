package io.mint.ai.agent.model;

import java.util.List;

public record EntitySpec(
        String name,
        String label,
        boolean mainEntity,
        List<FieldSpec> fields
) {
}
