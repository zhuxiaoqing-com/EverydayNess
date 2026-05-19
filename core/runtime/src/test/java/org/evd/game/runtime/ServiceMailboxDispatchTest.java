package org.evd.game.runtime;

import org.evd.game.runtime.call.Call;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.mailbox.MailboxExecutionMode;
import org.evd.game.runtime.mailbox.MailboxKey;
import org.evd.game.runtime.support.RpcCallException;
import org.evd.game.runtime.support.RpcErrorCodes;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceMailboxDispatchTest {
    @Test
    void orderedMailboxShouldSerializeCalls() throws Exception {
        TestMailboxService service = new TestMailboxService(new TestNode(nextAddr()), "ordered-service");
        MailboxKey mailboxKey = MailboxKey.player(1001L);
        service.registerMailboxForTest(mailboxKey, new Object(), MailboxExecutionMode.ORDERED);

        service.addMailboxCall(mailboxKey, 1, 20L);
        service.addMailboxCall(mailboxKey, 2, 20L);

        service.runPulseForTest();
        Thread.sleep(30L);
        service.runPulseForTest();
        Thread.sleep(30L);
        service.runPulseForTest();

        assertEquals(List.of("start-1", "end-1", "start-2", "end-2"), service.events());
    }

    @Test
    void unorderedMailboxShouldNotAcquireMailboxLock() throws Exception {
        TestMailboxService service = new TestMailboxService(new TestNode(nextAddr()), "unordered-service");
        MailboxKey mailboxKey = MailboxKey.map(2002L);
        service.registerMailboxForTest(mailboxKey, new Object(), MailboxExecutionMode.UNORDERED);

        service.addMailboxCall(mailboxKey, 1, 20L);
        service.addMailboxCall(mailboxKey, 2, 20L);

        service.runPulseForTest();

        assertTrue(service.events().size() >= 2);
        assertEquals(List.of("start-1", "start-2"), service.events().subList(0, 2));
    }

    @Test
    void missingMailboxShouldStillThrowForNormalMailboxCall() throws Exception {
        TestMailboxService service = new TestMailboxService(new TestNode(nextAddr()), "missing-normal");

        RpcCallException exception = assertThrows(RpcCallException.class,
                () -> service.dispatch_st(service.newMailboxCall(MailboxKey.player(3003L), TestMailboxService.METHOD_SLEEP_AND_RECORD, new Object[]{1, 1L})));

        assertEquals(RpcErrorCodes.MAILBOX_NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().startsWith("rpc mailbox not found: mailboxKey="));
    }

    @Test
    void missingMailboxShouldStillThrowForLocationForwardMailboxCall() throws Exception {
        TestMailboxService service = new TestMailboxService(new TestNode(nextAddr()), "missing-forward");

        RpcCallException exception = assertThrows(RpcCallException.class,
                () -> service.dispatch_st(service.newMailboxCall(
                        MailboxKey.player(4004L),
                        Integer.MIN_VALUE + 1,
                        new Object[]{TestMailboxService.METHOD_SLEEP_AND_RECORD, new Object[]{1, 1L}})));

        assertEquals(RpcErrorCodes.MAILBOX_NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().startsWith("rpc mailbox not found: mailboxKey="));
    }

    private static String nextAddr() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return "tcp://127.0.0.1:" + socket.getLocalPort();
        }
    }

    private static final class TestNode extends Node {
        private TestNode(String addr) {
            super("test-node-" + System.nanoTime(), addr);
        }
    }

    static final class TestMailboxService extends Service {
        static final int METHOD_SLEEP_AND_RECORD = 1;

        private final List<String> events = new ArrayList<>();

        TestMailboxService(Node node, String name) {
            super(node, name, "test-scheduled");
        }

        void registerMailboxForTest(MailboxKey key, Object mailbox, MailboxExecutionMode executionMode) {
            registerMailbox(key, mailbox, executionMode);
        }

        void addMailboxCall(MailboxKey key, int marker, long sleepMillis) {
            addCall_snt(newMailboxCall(key, METHOD_SLEEP_AND_RECORD, new Object[]{marker, sleepMillis}));
        }

        Call newMailboxCall(MailboxKey key, int methodKey, Object[] params) {
            Call call = new Call();
            call.from = new CallPoint("test-node", "from");
            call.to = new CallPoint(node.getId(), getId());
            call.mailboxKey = new MailboxKey(key);
            call.methodKey = methodKey;
            call.methodParam = params;
            return call;
        }

        List<String> events() {
            return new ArrayList<>(events);
        }

        void runPulseForTest() {
            pulseCase_t();
        }

        void sleepAndRecord(int marker, long sleepMillis) {
            events.add("start-" + marker);
            sleep(sleepMillis);
            events.add("end-" + marker);
        }

        @Override
        public void tick() {
        }

        @Override
        public void init() {
        }
    }

    public static final class TestMailboxServiceImpl extends RPCImplBase {
        @Override
        public Object getMethodFunction(Service service, int methodKey) {
            TestMailboxService mailboxService = (TestMailboxService) service;
            if (methodKey == TestMailboxService.METHOD_SLEEP_AND_RECORD) {
                return (org.evd.game.runtime.support.function.Function2<Integer, Long>) mailboxService::sleepAndRecord;
            }
            throw new IllegalArgumentException("unknown methodKey: " + methodKey);
        }
    }
}
