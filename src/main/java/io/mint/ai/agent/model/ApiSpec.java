package io.mint.ai.agent.model;

import java.util.List;

public record ApiSpec(
        String basePath,
        String pageRequestType,
        String pageResponseType,
        List<ApiEndpointSpec> endpoints,
        List<TypeSpec> requestTypes,
        List<TypeSpec> responseTypes,
        List<TableColumnMetaSpec> tableColumns,
        List<FormFieldMetaSpec> formSchema,
        List<DetailFieldMetaSpec> detailSchema
) {
}