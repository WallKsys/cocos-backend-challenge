package com.cocos.challenge.contract;

import com.cocos.challenge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@TestPropertySource(properties = "cocos.internal-tools.enabled=true")
class PublishedContractMatchesRoutesIT {

    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "post", "put", "delete", "patch", "options", "head", "trace");

    @Autowired
    RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void publishedRoutesExactlyMatchApplicationRoutes() throws IOException {
        assertThat(applicationRoutes()).isEqualTo(documentedRoutes());
    }

    private Set<Route> applicationRoutes() {
        return requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(mapping -> mapping.getPathPatternsCondition().getPatterns().stream()
                        .map(pattern -> pattern.getPatternString())
                        .filter(path -> path.startsWith("/api/"))
                        .flatMap(path -> methods(mapping).map(method -> new Route(method, path))))
                .collect(Collectors.toSet());
    }

    private Stream<String> methods(RequestMappingInfo mapping) {
        Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
        return methods.isEmpty()
                ? Arrays.stream(RequestMethod.values()).map(Enum::name)
                : methods.stream().map(Enum::name);
    }

    @SuppressWarnings("unchecked")
    private Set<Route> documentedRoutes() throws IOException {
        Map<String, Object> document;
        try (InputStream input = new ClassPathResource("static/openapi/openapi.yaml").getInputStream()) {
            document = new Yaml().load(input);
        }
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) document.get("paths");
        return paths.entrySet().stream()
                .flatMap(path -> path.getValue().entrySet().stream()
                        .filter(operation -> HTTP_METHODS.contains(operation.getKey()))
                        .map(operation -> new Route(
                                operation.getKey().toUpperCase(Locale.ROOT), path.getKey())))
                .collect(Collectors.toSet());
    }

    private record Route(String method, String path) {
    }
}
