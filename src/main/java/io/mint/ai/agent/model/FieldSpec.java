package io.mint.ai.agent.model;

public record FieldSpec(
        String name,
        String label,
        String dataType,
        boolean required,
        String formType,
        boolean queryable,
        boolean listable,
        boolean sortable,
        boolean detailVisible,
        String comment
) {
}
