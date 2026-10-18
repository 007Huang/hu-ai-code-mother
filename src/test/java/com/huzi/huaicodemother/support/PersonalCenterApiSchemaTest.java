package com.huzi.huaicodemother.support;

import com.huzi.huaicodemother.controller.AppController;
import com.huzi.huaicodemother.controller.UserController;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 从当前编译后的 DTO 和控制器生成新增 API 契约，不启动生产配置或外部服务。 */
class PersonalCenterApiSchemaTest {

    @Test
    void exportPersonalCenterApiAdditions() throws Exception {
        OpenAPI api = new OpenAPI()
                .openapi("3.0.1")
                .paths(new Paths())
                .components(new Components());
        addOperation(api, UserController.class, "updateMyUser", "user-controller");
        addOperation(api, UserController.class, "getUserQuota", "user-controller");
        addOperation(api, UserController.class, "updateUserQuota", "user-controller");
        addOperation(api, AppController.class, "getMyAppQuota", "app-controller");
        assertEquals(4, api.getPaths().size());
        Path output = Path.of("target", "personal-center-api-additions.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, Json.pretty(api));
    }

    private void addOperation(OpenAPI api, Class<?> controller, String name, String tag) {
        Method method = Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst().orElseThrow();
        RequestMapping parentMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        assertNotNull(parentMapping);
        assertNotNull(mapping);
        String path = parentMapping.value()[0] + mapping.value()[0];
        Operation operation = new Operation().operationId(name).addTagsItem(tag);
        operation.responses(new ApiResponses().addApiResponse("200", new ApiResponse()
                .description("OK")
                .content(jsonContent(schema(api, method.getGenericReturnType())))));
        for (java.lang.reflect.Parameter parameter : method.getParameters()) {
            if (parameter.isAnnotationPresent(org.springframework.web.bind.annotation.RequestBody.class)) {
                operation.requestBody(new RequestBody().required(true)
                        .content(jsonContent(schema(api, parameter.getParameterizedType()))));
            } else if (parameter.getType() == long.class || parameter.getType() == Long.class) {
                RequestParam annotation = parameter.getAnnotation(RequestParam.class);
                String parameterName = annotation != null && !annotation.value().isEmpty()
                        ? annotation.value() : parameter.getName();
                operation.addParametersItem(new Parameter().name(parameterName).in("query").required(true)
                        .schema(schema(api, parameter.getParameterizedType())));
            }
        }
        PathItem item = new PathItem();
        if (mapping.method()[0] == RequestMethod.GET) {
            item.get(operation);
        } else {
            item.post(operation);
        }
        api.getPaths().addPathItem(path, item);
    }

    private Schema<?> schema(OpenAPI api, Type type) {
        ResolvedSchema resolved = ModelConverters.getInstance().resolveAsResolvedSchema(
                new AnnotatedType(type).resolveAsRef(true));
        resolved.referencedSchemas.forEach(api.getComponents()::addSchemas);
        return resolved.schema;
    }

    private Content jsonContent(Schema<?> schema) {
        return new Content().addMediaType("application/json", new MediaType().schema(schema));
    }
}
