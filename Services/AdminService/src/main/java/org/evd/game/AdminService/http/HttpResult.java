package org.evd.game.AdminService.http;

/**
 * 管理端 HTTP 统一响应。
 */
public record HttpResult<T>(int status, String message, T data) {

    public static <T> HttpResult<T> ok(T data) {
        return new HttpResult<>(200, "ok", data);
    }

    public static HttpResult<Void> ok() {
        return new HttpResult<>(200, "ok", null);
    }

    public static HttpResult<Void> ok(String message) {
        return new HttpResult<>(200, message, null);
    }

    public static HttpResult<Void> fail(String message) {
        return new HttpResult<>(500, message, null);
    }
}
