package org.evd.game.runtime.client;

import org.evd.game.runtime.Session;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NettyClientTransportTest {

    @Test
    void shouldReceiveAndSendFramedPackets() throws Exception {
        int port = reservePort();
        AtomicReference<Session> sessionRef = new AtomicReference<>();
        AtomicInteger inboundMsgId = new AtomicInteger();
        AtomicReference<byte[]> inboundBody = new AtomicReference<>();
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch packetReceived = new CountDownLatch(1);

        NettyClientTransport transport = new NettyClientTransport(
                new NettyServerConfig("127.0.0.1", port),
                new ClientTransportHandler() {
                    @Override
                    public void onConnected(Session session) {
                        sessionRef.set(session);
                        connected.countDown();
                    }

                    @Override
                    public void onDisconnected(Session session) {
                    }

                    @Override
                    public void onPacket(Session session, int msgId, byte[] body) {
                        inboundMsgId.set(msgId);
                        inboundBody.set(body);
                        packetReceived.countDown();
                    }

                    @Override
                    public void onException(Session session, Throwable cause) {
                        throw new AssertionError(cause);
                    }
                });

        transport.start();
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            assertTrue(connected.await(5, TimeUnit.SECONDS), "客户端连接事件未触发");

            byte[] clientPayload = "hello".getBytes(StandardCharsets.UTF_8);
            writeFrame(socket, 1001, clientPayload);

            assertTrue(packetReceived.await(5, TimeUnit.SECONDS), "服务端未收到客户端帧");
            assertEquals(1001, inboundMsgId.get());
            assertArrayEquals(clientPayload, inboundBody.get());

            Session session = sessionRef.get();
            assertNotNull(session, "连接 session 不应为空");

            byte[] serverPayload = "world".getBytes(StandardCharsets.UTF_8);
            transport.send(session.getSessionId(), 2001, serverPayload);

            DataInputStream input = new DataInputStream(socket.getInputStream());
            int frameLength = input.readInt();
            assertEquals(Integer.BYTES + serverPayload.length, frameLength);
            assertEquals(2001, input.readInt());
            byte[] receivedBody = input.readNBytes(serverPayload.length);
            assertArrayEquals(serverPayload, receivedBody);
        } finally {
            transport.stop();
        }
    }

    private static int reservePort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }

    private static void writeFrame(Socket socket, int msgId, byte[] body) throws IOException {
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());
        output.writeInt(Integer.BYTES + body.length);
        output.writeInt(msgId);
        output.write(body);
        output.flush();
    }
}
