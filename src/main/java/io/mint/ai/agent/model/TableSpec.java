package io.mint.ai.agent.model;

import java.util.List;

public record TableSpec(
        String tableName,
        String comment,
        List<ColumnSpec> columns,
        List<String> indexes
) {
}
