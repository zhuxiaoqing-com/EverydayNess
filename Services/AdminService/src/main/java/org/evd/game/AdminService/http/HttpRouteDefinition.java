package org.evd.game.AdminService.http;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 一条 HTTP 路由的完整反射结果。
 */
public record HttpRouteDefinition(String routeKey,
                                  Object controller,
                                  Method method,
                                  List<HttpRouteParameter> parameters,
                                  Class<?> returnType,
                                  RequestType requestType) {
    public HttpRouteDefinition {
        parameters = List.copyOf(parameters);
    }

    public String methodName() {
        return method.getName();
    }

    public String controllerClassName() {
        return controller.getClass().getName();
    }
}
