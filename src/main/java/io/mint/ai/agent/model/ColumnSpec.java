package io.mint.ai.agent.model;

public record ColumnSpec(
        String name,
        String type,
        boolean nullable,
        boolean primaryKey,
        boolean autoIncrement,
        String defaultValue,
        String comment
) {
}