package org.evd.game.runtime;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * @author zhuxiaoqing
 * @Description: SnowflakeIdGenerator
 * @Date 2026/1/15 14:42
 **/
public final class SnowflakeIdGenerator {

    /**
     *
     * | 字段             | bit | 说明                  |
     * | -------------- | --- | ------------------- |
     * | sign           | 1   | 固定 0                |
     * | platform       | 9   | 0~511               |
     * | playerServerId | 15  | 0~32767             |
     * | epochSecond    | 29  | 自定义 epoch 起，536,870,912 秒，约 17.02 年回绕一次 |
     * | sequence       | 10  | 秒内 0~1023           |
     *
     */

    // ================== bit width ==================
    private static final int PLATFORM_BITS      = 9;   // 平台ID 0~511
    private static final int PLAYER_SERVER_BITS = 15;  // 服务器ID 0~32767
    private static final int SECOND_BITS        = 29;  // com.youxi.SnowflakeIdGenerator#SECOND_BITS，秒（相对 epoch 或逻辑秒），536,870,912 秒，约 17.02 年回绕一次
    private static final int SEQ_BITS           = 10;  // 秒内自增 0~1023

    // ================== bit position ==================
    private static final int SEQ_POS           = 0;
    private static final int SECOND_POS        = SEQ_POS + SEQ_BITS;
    private static final int SERVER_POS        = SECOND_POS + SECOND_BITS;
    private static final int PLATFORM_POS      = SERVER_POS + PLAYER_SERVER_BITS;


    // ================== max value ==================
    private static final long MAX_SEQ           = (1L << SEQ_BITS) - 1;
    private static final long MAX_SECOND        = (1L << SECOND_BITS) - 1;
    private static final long MAX_SERVER_ID     = (1L << PLAYER_SERVER_BITS) - 1;
    private static final long MAX_PLATFORM_ID   = (1L << PLATFORM_BITS) - 1;

    // ===== runtime state =====
    private static int  lastSecond;
    private static long seq;

    private static int platform;
    private static int playerServerId;

    // ================== time ==================
    // 自定义纪元（建议写死在配置）
    private static final long EPOCH_MILL;

    static {
        /**
         * d
         * 不同服务器 时区不同
         * 跨机房 / 跨国家直接炸
         * 同一秒生成的 ID，epochSecond 不一致
         */
        EPOCH_MILL =  LocalDate.of(2024, 1, 1)
                //.atStartOfDay(ZoneId.systemDefault())
                .atStartOfDay()
                .toInstant(ZoneOffset.of("8")).toEpochMilli();
    }

    public static void init(int _platform, int _serverId) {
        if (_platform > MAX_PLATFORM_ID) {
            throw new RuntimeException("platform is too big : " + _platform);
        }
        if (_serverId > MAX_SERVER_ID) {
            throw new RuntimeException("serverId is too big : " + _serverId);
        }
        platform = _platform;
        playerServerId = _serverId;

    }

    public synchronized static long createPlayerId() {
        int nowSecond = currentTimeSecond();

        if (nowSecond > lastSecond) {
            lastSecond = nowSecond;
            seq = 0;
        } else {
            seq++;
            if (seq > MAX_SEQ) {
                seq = 0;
                lastSecond++; // 借用下一秒
            }
        }

        if (lastSecond > MAX_SECOND) {
            throw new IllegalStateException("epochSecond overflow");
        }

        return ((long) platform       << PLATFORM_POS)
                | ((long) playerServerId << SERVER_POS)
                | ((long) lastSecond     << SECOND_POS)
                | seq;
    }

    private static int currentTimeSecond() {
        return (int) ((System.currentTimeMillis() - EPOCH_MILL) / 1000);
    }
}

