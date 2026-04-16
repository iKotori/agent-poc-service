package io.mint.ai.agent.model;

import java.util.List;

public record TypeSpec(
        String name,
        List<TypeFieldSpec> fields
) {
}
