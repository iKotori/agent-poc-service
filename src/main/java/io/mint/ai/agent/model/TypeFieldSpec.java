package io.mint.ai.agent.model;

public record TypeFieldSpec(
        String name,
        String type,
        boolean required,
        String comment
) {
}