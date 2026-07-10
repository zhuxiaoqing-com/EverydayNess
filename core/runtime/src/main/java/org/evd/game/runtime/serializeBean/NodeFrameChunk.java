package org.evd.game.runtime.serializeBean;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.evd.game.runtime.serialize.InputStream;

/**
 * 节点间网络线包: len | body
 */
public final class NodeFrameChunk {
    private final byte[] buffer;

    private NodeFrameChunk(byte[] buffer) {
        this.buffer = buffer;
    }

    public static NodeFrameChunk wrap(byte[] payload, int payloadLength) {
        byte[] framed = new byte[Integer.BYTES + payloadLength];
        writeFrameLength(framed, payloadLength);
        System.arraycopy(payload, 0, framed, Integer.BYTES, payloadLength);
        return new NodeFrameChunk(framed);
    }

    public ByteBuf getByteBuf() {
        return Unpooled.wrappedBuffer(buffer);
    }

    public int getFrameLength() {
        return buffer.length;
    }

    public InputStream newPayloadInputStream() {
        return new InputStream(buffer, Integer.BYTES, buffer.length - Integer.BYTES);
    }

    private static void writeFrameLength(byte[] target, int value) {
        target[0] = (byte) (value >>> 24);
        target[1] = (byte) (value >>> 16);
        target[2] = (byte) (value >>> 8);
        target[3] = (byte) value;
    }
}
