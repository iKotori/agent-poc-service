package io.mint.ai.agent.model;

import java.util.List;

public record ApiSpec(
        String basePath,
        List<ApiEndpointSpec> endpoints,
        List<TypeSpec> requestTypes,
        List<TypeSpec> responseTypes
) {
}