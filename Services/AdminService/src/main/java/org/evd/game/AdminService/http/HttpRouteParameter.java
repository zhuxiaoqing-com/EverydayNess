package org.evd.game.AdminService.http;

import java.lang.reflect.Type;

/**
 * HTTP 路由方法的参数元数据。
 */
public record HttpRouteParameter(int index, String name, Class<?> parameterType, Type genericType) {
}
