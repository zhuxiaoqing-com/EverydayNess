package org.evd.game.PlayerService.timer;

import org.evd.game.PlayerService.PlayerService;
import org.evd.game.PlayerService.dbDef.db.bean.DBPlayerData;
import org.evd.game.PlayerService.dbDef.db.table.DBPlayerDataTable;
import org.evd.game.PlayerService.event.RoleHourEvent;
import org.evd.game.PlayerService.event.RoleMinuteEvent;
import org.evd.game.PlayerService.event.RoleMidnightEvent;
import org.evd.game.PlayerService.event.SystemFiveMinuteEvent;
import org.evd.game.PlayerService.event.SystemHalfHourEvent;
import org.evd.game.PlayerService.event.SystemHourEvent;
import org.evd.game.PlayerService.event.SystemMinuteEvent;
import org.evd.game.PlayerService.event.SystemSecondEvent;
import org.evd.game.PlayerService.session.PPlayerOnline;
import org.evd.game.runtime.util.TimeUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** PlayerService 的秒级定时器，负责派发系统和玩家周期事件。 */
public final class PlayerTimer {
    public static final long INTERVAL_MILLIS = TimeUtils.SEC;

    private final PlayerService owner;
    private long lastSecond = -1;
    private int lastMinute = -1;
    private int lastHour = -1;
    private LocalDate lastDay;

    public PlayerTimer(PlayerService owner) {
        this.owner = owner;
    }

    /** 每秒触发一次，按服务器当前自然时间判断更长周期。 */
    public void onSecond() {
        long currentMill = owner.getTimeCurrent();
        LocalDateTime currentTime = Instant.ofEpochMilli(currentMill)
                .atZone(TimeUtils.ZONE_ID).toLocalDateTime();
        long currentSecond = currentMill / TimeUtils.SEC;
        int currentMinute = currentTime.getMinute();
        int currentHour = currentTime.getHour();
        LocalDate currentDay = currentTime.toLocalDate();

        if (currentSecond != lastSecond) {
            lastSecond = currentSecond;

            owner.publishEvent(SystemSecondEvent.Listener.class,
                    SystemSecondEvent.INSTANCE, SystemSecondEvent.Listener::onEvent);
        }
        boolean newDay = !currentDay.equals(lastDay);
        if (newDay) {
            lastDay = currentDay;
            publishPlayerMidnightEvent(currentMill);
        }

        boolean newMinute = newDay || currentMinute != lastMinute;
        if (newMinute) {
            lastMinute = currentMinute;
            owner.publishEvent(SystemMinuteEvent.Listener.class, SystemMinuteEvent.INSTANCE, SystemMinuteEvent.Listener::onEvent);
            publishPlayerMinuteEvent();

            if (currentTime.getMinute() % 5 == 0) {
                owner.publishEvent(SystemFiveMinuteEvent.Listener.class, SystemFiveMinuteEvent.INSTANCE, SystemFiveMinuteEvent.Listener::onEvent);
            }
            if (currentTime.getMinute() % 30 == 0) {
                owner.publishEvent(SystemHalfHourEvent.Listener.class, SystemHalfHourEvent.INSTANCE, SystemHalfHourEvent.Listener::onEvent);
            }
            boolean newHour = newDay || currentHour != lastHour;
            if (newHour) {
                lastHour = currentHour;
                owner.publishEvent(SystemHourEvent.Listener.class, SystemHourEvent.INSTANCE, SystemHourEvent.Listener::onEvent);
                publishPlayerHourEvent();
            }
        }

    }

    private void publishPlayerMidnightEvent(long currentMill) {
        for (PPlayerOnline player : owner.sessionManager().onlinePlayers()) {
            if (player.getStatus() != PPlayerOnline.Status.ONLINE) {
                continue;
            }
            DBPlayerData data = DBPlayerDataTable.get(player.getPlayerId());
            if (TimeUtils.isSameDay(data.getLastMidnightMill(), currentMill)) {
                continue;
            }
            data.setLastMidnightMill(currentMill);
            RoleMidnightEvent event = new RoleMidnightEvent(player.getPlayerId());
            owner.publishEvent(RoleMidnightEvent.Listener.class, event, RoleMidnightEvent.Listener::onEvent);
        }
    }

    private void publishPlayerMinuteEvent() {
        for (PPlayerOnline player : owner.sessionManager().onlinePlayers()) {
            if (player.getStatus() != PPlayerOnline.Status.ONLINE) {
                continue;
            }
            RoleMinuteEvent event = new RoleMinuteEvent(player.getPlayerId());
            owner.publishEvent(RoleMinuteEvent.Listener.class, event, RoleMinuteEvent.Listener::onEvent);
        }
    }

    private void publishPlayerHourEvent() {
        for (PPlayerOnline player : owner.sessionManager().onlinePlayers()) {
            if (player.getStatus() != PPlayerOnline.Status.ONLINE) {
                continue;
            }
            RoleHourEvent event = new RoleHourEvent(player.getPlayerId());
            owner.publishEvent(RoleHourEvent.Listener.class, event, RoleHourEvent.Listener::onEvent);
        }
    }
}
