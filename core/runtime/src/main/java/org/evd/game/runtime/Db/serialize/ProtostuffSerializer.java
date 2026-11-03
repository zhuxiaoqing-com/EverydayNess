package org.evd.game.runtime.Db.serialize;

import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesOut;
import org.evd.game.runtime.misc.BufferPool;

import java.util.Objects;

/** Service 线程串行复用的 Protostuff 编解码缓冲区。 */
public final class ProtostuffSerializer implements CachedProtostuffSerializer, AutoCloseable {
    private byte[] output;
    private LinkedBuffer buffer;

    public ProtostuffSerializer() {
        output = BufferPool.allocate();
        buffer = LinkedBuffer.use(output);
    }

    /** 返回独立结果，供需要长期持有序列化内容的 DBReq 使用。 */
    public <T> byte[] serialize(T value, Schema<T> schema) {
        LinkedBuffer currentBuffer = requireBuffer();
        try {
            return ProtostuffIOUtil.toByteArray(value, schema, currentBuffer);
        } finally {
            currentBuffer.clear();
        }
    }

    /** 序列化并将内容及其长度写入目标，返回序列化长度。 */
    @Override
    public <T> int writeCachedProtostuff(BytesOut<?> bytes, T value, Schema<T> schema) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(schema, "schema");
        LinkedBuffer currentBuffer = requireBuffer();
        try {
            int serializedLength = ProtostuffIOUtil.writeTo(currentBuffer, value, schema);
            if (serializedLength > output.length) {
                throw new IllegalStateException("Protostuff 序列化超过复用缓冲区: length="
                        + serializedLength + ", capacity=" + output.length);
            }
            bytes.writeInt(serializedLength);
            bytes.write(output, 0, serializedLength);
            return serializedLength;
        } finally {
            currentBuffer.clear();
        }
    }

    /** 从 Chronicle 输入读入复用缓冲区，并立即合并到目标对象。 */
    @Override
    public <T> void readWithProtostuff(BytesIn<?> bytes, int serializedLength,
                                       T value, Schema<T> schema) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(schema, "schema");
        LinkedBuffer currentBuffer = requireBuffer();
        try {
            if (serializedLength < 0 || serializedLength > bytes.readRemaining()) {
                throw new IllegalArgumentException("输入的 Protostuff 长度无效: length="
                        + serializedLength + ", remaining=" + bytes.readRemaining());
            }
            if (serializedLength > output.length) {
                throw new IllegalStateException("Protostuff 输入超过复用缓冲区: length="
                        + serializedLength + ", capacity=" + output.length);
            }
            bytes.read(output, 0, serializedLength);
            ProtostuffIOUtil.mergeFrom(output, 0, serializedLength, value, schema);
        } finally {
            currentBuffer.clear();
        }
    }

    @Override
    public void close() {
        if (buffer != null) {
            buffer.clear();
            buffer = null;
        }
        if (output != null) {
            BufferPool.deallocate(output);
            output = null;
        }
    }

    private LinkedBuffer requireBuffer() {
        if (buffer == null || output == null) {
            throw new IllegalStateException("ProtostuffSerializer 已关闭");
        }
        return buffer;
    }
}
