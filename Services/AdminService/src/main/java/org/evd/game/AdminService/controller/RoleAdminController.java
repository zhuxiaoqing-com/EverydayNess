package org.evd.game.AdminService.controller;

import org.evd.game.AdminService.http.HttpRequest;
import org.evd.game.AdminService.http.HttpResult;
import org.evd.game.runtime.annotation.RequestMapping;
import org.evd.game.runtime.support.LogCore;

/**
 * 角色管理 HTTP 接口。
 */
@RequestMapping("/admin/role")
public class RoleAdminController {

    @RequestMapping("/query")
    public HttpResult<RoleVO> queryRole(HttpRequest ctx, QueryRoleRequest request) {
        long roleId = requireRoleId(request.roleId());

        return HttpResult.ok(new RoleVO(roleId));
    }

    @RequestMapping("/ban")
    public HttpResult<Void> banRole(HttpRequest ctx, BanRoleRequest request) {
        long roleId = requireRoleId(request.roleId());
        int minutes = requireMinutes(request.minutes());
        String reason = requireReason(request.reason());

        LogCore.core.info("AdminService banRole 请求: roleId={}, minutes={}, reason={}, clientAddr={}",
                roleId, minutes, reason, ctx.getClientAddr());

        return HttpResult.ok();
    }

    private static long requireRoleId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("roleId 必须大于 0");
        }
        return value;
    }

    private static int requireMinutes(Integer value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("minutes 必须大于 0");
        }
        return value;
    }

    private static String requireReason(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("reason 不能为空");
        }
        return value.trim();
    }

    public record QueryRoleRequest(Long roleId) {
    }

    public record BanRoleRequest(Long roleId, Integer minutes, String reason) {
    }
}
