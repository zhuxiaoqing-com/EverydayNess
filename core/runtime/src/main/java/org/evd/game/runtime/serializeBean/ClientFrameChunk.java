package org.evd.game.runtime.serializeBean;

import com.google.protobuf.Message;
import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;
import org.evd.game.base.InputStreamBase;
import org.evd.game.base.OutputStreamBase;
import org.evd.game.runtime.serialize.InputStream;

import java.io.IOException;

/**
 * 客户端网络线包: len | msgId | body
 */
@SerializeClass(customized = true)
public class ClientFrameChunk implements ISerializable {
    public int msgLength;
    public int msgId;
    public Message message;

    private byte[] bodyBuffer;

    public ClientFrameChunk() {
    }

    private ClientFrameChunk(int msgId, Message message) {
        this.msgLength =  message.getSerializedSize();
        this.msgId = msgId;
        this.message = message;
    }

    public static ClientFrameChunk wrap(int msgId, Message message) {
        return new ClientFrameChunk(msgId, message);
    }

    @Override
    public void writeTo(OutputStreamBase stream) throws IOException {
        byte[] body = requireBodyBuffer();
        stream.writeInt(msgLength);
        stream.writeInt(msgId);
        stream.writeBytes(body, 0, body.length);
    }

    @Override
    public void readFrom(InputStreamBase stream) throws IOException {
        msgLength = stream.readInt();
        msgId = stream.readInt();
        bodyBuffer = ((InputStream) stream).readRawBytes(msgLength);
        message = null;
    }

    public int getMsgLength() {
        return msgLength;
    }

    public ClientFrameChunk setMsgLength(int msgLength) {
        this.msgLength = msgLength;
        return this;
    }

    public int getMsgId() {
        return msgId;
    }

    public ClientFrameChunk setMsgId(int msgId) {
        this.msgId = msgId;
        return this;
    }

    public Message getMessage() {
        return message;
    }

    public ClientFrameChunk setMessage(Message message) {
        this.message = message;
        this.bodyBuffer = null;
        this.msgLength = message == null ? 0 : message.getSerializedSize();
        return this;
    }

    public byte[] getBodyBuffer() {
        return bodyBuffer;
    }

    public byte[] requireBodyBuffer() throws IOException {
        return ensureBodyBuffer();
    }

    public ClientFrameChunk setBodyBuffer(byte[] bodyBuffer) {
        this.bodyBuffer = bodyBuffer;
        this.message = null;
        this.msgLength = bodyBuffer == null ? 0 : bodyBuffer.length;
        return this;
    }

    private byte[] ensureBodyBuffer() throws IOException {
        if (bodyBuffer != null) {
            return bodyBuffer;
        }
        if (message == null) {
            throw new IOException("ClientFrameChunk 缺少消息体: msgId=" + msgId);
        }
        bodyBuffer = message.toByteArray();
        msgLength = bodyBuffer.length;
        return bodyBuffer;
    }
}
