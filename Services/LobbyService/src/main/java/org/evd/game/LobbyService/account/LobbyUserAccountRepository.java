package org.evd.game.LobbyService.account;

import org.evd.game.LobbyService.dbDef.db.bean.LBRole;
import org.evd.game.LobbyService.dbDef.db.bean.LBUserAccount;
import org.evd.game.LobbyService.dbDef.db.table.LBRoleTable;
import org.evd.game.LobbyService.dbDef.db.table.LBUserAccountTable;
import org.evd.game.common.serializeBean.LobbyService.LobbyUserAccessResult;
import org.evd.game.runtime.support.LogCore;

/** Lobby 用户账号仓库，负责账号、角色数据和封禁状态。 */
public final class LobbyUserAccountRepository {
    public static final int STATUS_NORMAL = 0;
    public static final int STATUS_BANNED = 1;

    /** 查询用户；不存在时创建，存在但封禁时拒绝登录。 */
    public LobbyUserAccessResult validateOrCreate(String userId, long now) {
        if (userId == null || userId.isBlank()) {
            return LobbyUserAccessResult.denied("userId 不能为空");
        }

        LBUserAccount account = LBUserAccountTable.get(userId);
        if (account == null) {
            LBUserAccount created = new LBUserAccount();
            created.setUserId(userId);
            created.setStatus(STATUS_NORMAL);
            created.setBanReason("");
            created.setCreateTime(now);
            created.setLastLoginTime(now);
            if (LBUserAccountTable.add(userId, created, true)) {
                LogCore.core.info("LobbyService 创建用户账号: userId={}", userId);
                return LobbyUserAccessResult.allowed(true);
            }

            // 并发首登时另一请求可能已经完成创建，重新读取后继续按真实状态判断。
            account = LBUserAccountTable.get(userId);
            if (account == null) {
                LogCore.core.error("LobbyService 创建用户账号失败: userId={}", userId);
                return LobbyUserAccessResult.denied("用户数据创建失败");
            }
        }

        if (account.getStatus() == STATUS_BANNED) {
            String reason = account.getBanReason();
            return LobbyUserAccessResult.denied(
                    reason == null || reason.isBlank() ? "用户已被封禁" : "用户已被封禁: " + reason);
        }
        if (account.getStatus() != STATUS_NORMAL) {
            LogCore.core.warn("LobbyService 用户账号状态非法: userId={}, status={}",
                    userId, account.getStatus());
            return LobbyUserAccessResult.denied("用户状态非法");
        }

        account.setLastLoginTime(now);
        return LobbyUserAccessResult.allowed(false);
    }

    /** 获取用户账号数据；登录状态不保存在账号对象中。 */
    public LBUserAccount findAccount(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return LBUserAccountTable.get(userId);
    }

    /** 创建并持久化用户角色；同一账号只允许创建一个角色。 */
    public boolean createRole(String userId, long playerId, int characterId, String name, int level) {
        LBUserAccount account = findNormalAccount(userId);
        if (account == null || playerId <= 0L
                || !account.getPlayerIds().isEmpty()) {
            LogCore.core.warn("LobbyService 创建用户角色失败: userId={}, playerId={}, account={}",
                    userId, playerId, account);
            return false;
        }
        if (!account.getPlayerIds().isEmpty()) {
            return false;
        }
        LBRole roleData = new LBRole();
        roleData.setPlayerId(playerId);
        roleData.setUserId(userId);
        roleData.setName(name);
        roleData.setLevel(level);
        roleData.setCharacterId(characterId);
        if (!LBRoleTable.add(playerId, roleData, true)) {
            LogCore.core.warn("LobbyService 保存角色数据失败: userId={}, playerId={}",
                    userId, playerId);
            return false;
        }
        account.getPlayerIds().add(playerId);
        return true;
    }

    /** 从账号关联的角色 ID 加载角色详情。 */
    public LBRole loadRole(String userId) {
        LBUserAccount account = findAccount(userId);
        if (account == null || account.getPlayerIds().isEmpty()) {
            return null;
        }
        return loadRole(account.getPlayerIds().get(0));
    }

    /** 从角色表加载角色详情。 */
    public LBRole loadRole(long playerId) {
        return LBRoleTable.get(playerId);
    }

    private LBUserAccount findNormalAccount(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        LBUserAccount account = findAccount(userId);
        return account != null && account.getStatus() == STATUS_NORMAL ? account : null;
    }
}
