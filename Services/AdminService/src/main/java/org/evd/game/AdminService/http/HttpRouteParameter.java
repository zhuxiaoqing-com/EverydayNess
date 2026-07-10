package org.evd.game.AdminService.http;

/**
 * HTTP 路由方法的参数元数据。
 */
public record HttpRouteParameter(int index, Class<?> parameterType) {
}
