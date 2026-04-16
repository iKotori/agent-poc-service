package io.mint.ai.agent.model;

import java.util.List;

public record SqlSpec(
        List<TableSpec> tables,
        String ddl
) {
}
