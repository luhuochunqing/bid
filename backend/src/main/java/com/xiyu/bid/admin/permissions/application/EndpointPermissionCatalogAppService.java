// Input: Spring MVC handler mappings and endpoint permission pure policy
// Output: sorted endpoint permission matrix DTO rows
// Pos: admin permissions application service
package com.xiyu.bid.admin.permissions.application;

import com.xiyu.bid.admin.permissions.core.EndpointPermissionDescriptor;
import com.xiyu.bid.admin.permissions.core.EndpointPermissionPolicy;
import com.xiyu.bid.admin.permissions.dto.EndpointPermissionItem;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class EndpointPermissionCatalogAppService {
    private final RequestMappingHandlerMapping handlerMapping;
    private final EndpointPermissionPolicy policy = new EndpointPermissionPolicy();

    public EndpointPermissionCatalogAppService(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping
    ) {
        this.handlerMapping = handlerMapping;
    }

    public List<EndpointPermissionItem> listEndpointPermissions() {
        return handlerMapping.getHandlerMethods().entrySet().stream()
                .flatMap(entry -> toItems(entry).stream())
                .filter(item -> item.path().startsWith("/api/"))
                .sorted(Comparator
                        .comparing(EndpointPermissionItem::path)
                        .thenComparing(EndpointPermissionItem::method))
                .toList();
    }

    private List<EndpointPermissionItem> toItems(Map.Entry<RequestMappingInfo, HandlerMethod> entry) {
        Set<String> paths = entry.getKey().getPatternValues();
        Set<org.springframework.web.bind.annotation.RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
        Set<String> safePaths = paths.isEmpty() ? Set.of("/") : paths;
        Set<String> safeMethods = methods.isEmpty()
                ? Set.of("ANY")
                : methods.stream().map(Enum::name).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        return safePaths.stream()
                .flatMap(path -> safeMethods.stream().map(method -> describe(method, path, entry.getValue())))
                .map(this::toItem)
                .toList();
    }

    private EndpointPermissionDescriptor describe(String method, String path, HandlerMethod handlerMethod) {
        return policy.describe(
                method,
                path,
                handlerMethod.getBeanType().getSimpleName(),
                handlerMethod.getMethod().getName(),
                preAuthorizeExpression(handlerMethod)
        );
    }

    private String preAuthorizeExpression(HandlerMethod handlerMethod) {
        PreAuthorize methodAnnotation = handlerMethod.getMethodAnnotation(PreAuthorize.class);
        if (methodAnnotation != null) return methodAnnotation.value();

        PreAuthorize classAnnotation = handlerMethod.getBeanType().getAnnotation(PreAuthorize.class);
        return classAnnotation == null ? null : classAnnotation.value();
    }

    private EndpointPermissionItem toItem(EndpointPermissionDescriptor descriptor) {
        return new EndpointPermissionItem(
                descriptor.method(),
                descriptor.path(),
                descriptor.module(),
                descriptor.controller(),
                descriptor.handler(),
                descriptor.expression(),
                descriptor.allowedRoles(),
                descriptor.accessLevel(),
                descriptor.riskLevel(),
                descriptor.configurable(),
                descriptor.source(),
                descriptor.scopeNote()
        );
    }
}
