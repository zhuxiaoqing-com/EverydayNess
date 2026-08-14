package org.evd.game.runtime.util.id.idSegment;

/**
 * version 0：基于 ID 号段的布局。
 *
 * <pre>
 * | 字段             | bit | 范围/说明                              |
 * |------------------|-----|----------------------------------------|
 * | fixed            | 1   | 固定 1，最高位                         |
 * | version          | 1   | 固定 0                                 |
 * | platformId       | 8   | 0~255                                  |
 * | playerServerId   | 12  | 0~4095                                 |
 * | nodeId           | 6   | 0~63                                   |
 * | incrementId      | 36  | 0~68719476735，约 687 亿               |
 * </pre>
 *
 * <p>号段申请器只申请低位号段，最高位固定为 1，因此 Java 的 long 表示值会是负数。</p>
 */
public final class IdSegmentLayout0 extends IdSegmentLayout {

    private static final int PLATFORM_BITS = 8;
    private static final int PLAYER_SERVER_BITS = 12;
    private static final int NODE_BITS = 6;
    private static final int INCREMENT_BITS = 36;

    public IdSegmentLayout0(int platformId, int playerServerId, int nodeId) {
        super(PLATFORM_BITS, PLAYER_SERVER_BITS, NODE_BITS, INCREMENT_BITS,
                platformId, playerServerId, nodeId);
    }
}
