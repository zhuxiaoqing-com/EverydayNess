package org.evd.game.AdminService.http;

import org.evd.game.common.ClassFinder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AdminService 的 HTTP 控制器注册表。
 */
public final class AdminHttpRouteRegistry {
    //private static final String CONTROLLER_PACKAGE = "org.evd.game.AdminService.controller";

    private final Map<String, HttpRouteDefinition> routes;

    private AdminHttpRouteRegistry(Map<String, HttpRouteDefinition> routes) {
        this.routes = Collections.unmodifiableMap(new LinkedHashMap<>(routes));
    }

    public static AdminHttpRouteRegistry load(String path) {
        List<Object> controllers = ClassFinder.getAllClass(path).stream()
                .filter(AdminHttpRouteRegistry::isConcreteController)
                .map(AdminHttpRouteRegistry::newControllerInstance)
                .toList();
        HttpRequestMappingParser parser = new HttpRequestMappingParser();
        return new AdminHttpRouteRegistry(parser.parseControllers(controllers));
    }

    public Map<String, HttpRouteDefinition> routes() {
        return routes;
    }

    public Map<String, String> routeToMethodName() {
        LinkedHashMap<String, String> routeMap = new LinkedHashMap<>();
        routes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> routeMap.put(entry.getKey(), entry.getValue().methodName()));
        return Collections.unmodifiableMap(routeMap);
    }

    public HttpRouteDefinition getRequired(String path) {
        String routeKey = HttpRequestMappingParser.normalizePath(path);
        HttpRouteDefinition routeDefinition = routes.get(routeKey);
        if (routeDefinition == null) {
            throw new IllegalStateException("找不到 HTTP 路由: route=" + routeKey);
        }
        return routeDefinition;
    }

    private static boolean isConcreteController(Class<?> clazz) {
        int modifiers = clazz.getModifiers();
        return hasHttpRoute(clazz)
                && !clazz.isInterface()
                && !Modifier.isAbstract(modifiers);
    }

    private static boolean hasHttpRoute(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(HttpRoute.class)) {
                return true;
            }
        }
        return false;
    }

    private static Object newControllerInstance(Class<?> controllerClass) {
        try {
            Constructor<?> constructor = controllerClass.getDeclaredConstructor();
            if (!Modifier.isPublic(constructor.getModifiers())) {
                throw new IllegalStateException("HTTP Controller 必须提供 public 无参构造: "
                        + controllerClass.getName());
            }
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("实例化 HTTP Controller 失败: " + controllerClass.getName(), e);
        }
    }
}
