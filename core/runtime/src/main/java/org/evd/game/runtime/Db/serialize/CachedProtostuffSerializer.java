package org.evd.game.runtime.Db.serialize;

import io.protostuff.Schema;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesOut;

/** 供快照读写过程使用的复用 Protostuff 缓冲区接口。 */
public interface CachedProtostuffSerializer {
    <T> int writeCachedProtostuff(BytesOut<?> bytes, T value, Schema<T> schema);

    <T> void readWithProtostuff(BytesIn<?> bytes, int serializedLength,
                                T value, Schema<T> schema);
}
