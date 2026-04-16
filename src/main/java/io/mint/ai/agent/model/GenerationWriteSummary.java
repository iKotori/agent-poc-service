package io.mint.ai.agent.model;

import java.util.List;

public record GenerationWriteSummary(
        List<String> createdFiles,
        List<String> modifiedFiles,
        List<String> skippedFiles,
        List<String> assumptions,
        List<String> notes
) {
}
