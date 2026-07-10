package org.evd.game.AdminService.http;

import org.evd.game.runtime.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析自定义 @RequestMapping，产出可供 AdminService HTTP 分发使用的路由表。
 */
public final class HttpRequestMappingParser {

    public Map<String, HttpRouteDefinition> parseControllers(Object... controllers) {
        return parseControllers(Arrays.asList(controllers));
    }

    public Map<String, HttpRouteDefinition> parseControllers(Collection<?> controllers) {
        LinkedHashMap<String, HttpRouteDefinition> routes = new LinkedHashMap<>();
        for (Object controller : controllers) {
            parseController(controller, routes);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(routes));
    }

    public static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "/";
        }
        String normalized = rawPath.trim().replace('\\', '/');
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        normalized = normalized.replaceAll("/+", "/");
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public static String joinPaths(String classPath, String methodPath) {
        String normalizedClassPath = normalizePath(classPath);
        String normalizedMethodPath = normalizePath(methodPath);
        if ("/".equals(normalizedClassPath)) {
            return normalizedMethodPath;
        }
        if ("/".equals(normalizedMethodPath)) {
            return normalizedClassPath;
        }
        return normalizePath(normalizedClassPath + "/" + normalizedMethodPath.substring(1));
    }

    private void parseController(Object controller, Map<String, HttpRouteDefinition> routes) {
        if (controller == null) {
            throw new IllegalArgumentException("controller 不能为空");
        }

        Class<?> controllerClass = controller.getClass();
        RequestMapping classMapping = controllerClass.getAnnotation(RequestMapping.class);
        String classPath = classMapping == null ? "/" : classMapping.value();

        Method[] methods = controllerClass.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName).thenComparing(Method::toGenericString));
        for (Method method : methods) {
            RequestMapping methodMapping = method.getAnnotation(RequestMapping.class);
            if (methodMapping == null) {
                continue;
            }
            validateHandlerMethod(controllerClass, method);
            String routeKey = joinPaths(classPath, methodMapping.value());
            HttpRouteDefinition previous = routes.putIfAbsent(routeKey, buildRouteDefinition(routeKey, controller, method));
            if (previous != null) {
                throw new IllegalStateException("HTTP 路由重复注册: route=" + routeKey
                        + ", previous=" + previous.controllerClassName() + "#" + previous.methodName()
                        + ", current=" + controllerClass.getName() + "#" + method.getName());
            }
        }
    }

    private HttpRouteDefinition buildRouteDefinition(String routeKey, Object controller, Method method) {
        List<HttpRouteParameter> parameters = new ArrayList<>();
        Parameter[] reflectParameters = method.getParameters();
        for (int i = 0; i < reflectParameters.length; i++) {
            parameters.add(new HttpRouteParameter(i, reflectParameters[i].getType()));
        }
        return new HttpRouteDefinition(routeKey, controller, method, parameters, method.getReturnType());
    }

    private void validateHandlerMethod(Class<?> controllerClass, Method method) {
        int modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers)) {
            throw new IllegalStateException("HTTP 路由方法必须是 public: "
                    + controllerClass.getName() + "#" + method.getName());
        }
        if (Modifier.isStatic(modifiers)) {
            throw new IllegalStateException("HTTP 路由方法不能是 static: "
                    + controllerClass.getName() + "#" + method.getName());
        }
    }
}
