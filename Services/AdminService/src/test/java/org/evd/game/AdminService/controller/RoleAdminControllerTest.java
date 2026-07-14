package org.evd.game.AdminService.controller;

import com.alibaba.fastjson2.JSONObject;
import org.evd.game.AdminService.http.HttpRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleAdminControllerTest {

    private final ServerController controller = new ServerController();

    @Test
    void banRole_shouldReturnHttpResult() {
        HttpRequest request = newHttpRequest("/admin/role/ban");

        var result = controller.banRole(
                request,
                new ServerController.BanRoleRequest(10001L, 45, "spam")
        );

        assertEquals(200, result.status());
        assertEquals("ok", result.message());
        assertEquals(null, result.data());
    }

    @Test
    void queryRole_shouldReturnHttpResultWithRoleVO() {
        HttpRequest request = newHttpRequest("/admin/role/query");

        var result = controller.queryRole(request, 10001L);

        assertEquals(200, result.status());
        assertEquals(10001L, result.data().roleId());
    }

    @Test
    void queryRole_shouldRejectMissingRoleId() {
        HttpRequest request = newHttpRequest("/admin/role/query");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.queryRole(request, null)
        );

        assertEquals("roleId 必须大于 0", exception.getMessage());
    }

    private static HttpRequest newHttpRequest(String uri) {
        try {
            Constructor<HttpRequest> constructor = HttpRequest.class.getDeclaredConstructor(
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    JSONObject.class,
                    Map.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(
                    "POST",
                    uri,
                    uri,
                    "127.0.0.1:8080",
                    "",
                    null,
                    new LinkedHashMap<>()
            );
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("构造 HttpRequest 失败", e);
        }
    }
}
