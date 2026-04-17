package io.mint.ai.agent.model;

public record FormFieldMetaSpec(
        String field,
        String label,
        String component,
        boolean required
) {
}