package io.mint.ai.agent.model;

import java.util.List;

public record RunHistoryResponse(
        long total,
        int page,
        int size,
        List<RunHistoryItemResponse> items
) {
}
