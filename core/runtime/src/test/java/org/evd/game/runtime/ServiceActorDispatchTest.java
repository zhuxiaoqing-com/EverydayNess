package org.evd.game.runtime;

import org.evd.game.runtime.call.Call;
import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.actor.ActorExecutionMode;
import org.evd.game.runtime.actor.ActorId;
import org.evd.game.runtime.support.RpcCallException;
import org.evd.game.runtime.support.RpcErrorCodes;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceActorDispatchTest {
    @Test
    void orderedActorShouldSerializeCalls() throws Exception {
        TestActorService service = new TestActorService(new TestNode(nextAddr()), "ordered-service");
        ActorId actorId = ActorId.player(1001L);
        service.registerActorForTest(actorId, new Object(), ActorExecutionMode.ORDERED);

        service.addActorCall(actorId, 1, 20L);
        service.addActorCall(actorId, 2, 20L);

        service.runPulseForTest();
        Thread.sleep(30L);
        service.runPulseForTest();
        Thread.sleep(30L);
        service.runPulseForTest();

        assertEquals(List.of("start-1", "end-1", "start-2", "end-2"), service.events());
    }

    @Test
    void unorderedActorShouldNotAcquireActorLock() throws Exception {
        TestActorService service = new TestActorService(new TestNode(nextAddr()), "unordered-service");
        ActorId actorId = ActorId.map(2002L);
        service.registerActorForTest(actorId, new Object(), ActorExecutionMode.UNORDERED);

        service.addActorCall(actorId, 1, 20L);
        service.addActorCall(actorId, 2, 20L);

        service.runPulseForTest();

        assertTrue(service.events().size() >= 2);
        assertEquals(List.of("start-1", "start-2"), service.events().subList(0, 2));
    }

    @Test
    void missingActorShouldStillThrowForNormalActorCall() throws Exception {
        TestActorService service = new TestActorService(new TestNode(nextAddr()), "missing-normal");

        RpcCallException exception = assertThrows(RpcCallException.class,
                () -> service.dispatch_st(service.newActorCall(ActorId.player(3003L), TestActorService.METHOD_SLEEP_AND_RECORD, new Object[]{1, 1L})));

        assertEquals(RpcErrorCodes.ACTOR_NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().startsWith("rpc actor not found: actorId="));
    }

    @Test
    void missingActorShouldStillThrowForLocationForwardActorCall() throws Exception {
        TestActorService service = new TestActorService(new TestNode(nextAddr()), "missing-forward");

        RpcCallException exception = assertThrows(RpcCallException.class,
                () -> service.dispatch_st(service.newForwardActorCall(
                        ActorId.player(4004L),
                        TestActorService.METHOD_SLEEP_AND_RECORD,
                        new Object[]{1, 1L})));

        assertEquals(RpcErrorCodes.ACTOR_NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().startsWith("rpc actor not found: actorId="));
    }

    @Test
    void locationForwardActorCallShouldDispatchBusinessMethod() throws Exception {
        TestActorService service = new TestActorService(new TestNode(nextAddr()), "forward-dispatch");
        ActorId actorId = ActorId.player(5005L);
        service.registerActorForTest(actorId, new Object(), ActorExecutionMode.UNORDERED);

        service.dispatch_st(service.newForwardActorCall(
                actorId,
                TestActorService.METHOD_SLEEP_AND_RECORD,
                new Object[]{7, 0L}));

        assertEquals(List.of("start-7", "end-7"), service.events());
    }

    @Test
    void orderedActorShouldDropQueuedMessageAfterActorUnregisters() throws Exception {
        TestActorService service = new TestActorService(new TestNode(nextAddr()), "ordered-stale-drop");
        ActorId actorId = ActorId.player(6006L);
        service.registerActorForTest(actorId, new Object(), ActorExecutionMode.ORDERED);
        service.scheduleAfterSleepHook(1, () -> service.unregisterActorForTest(actorId));

        service.addActorCall(actorId, 1, 20L);
        service.addActorCall(actorId, 2, 0L);

        service.runPulseForTest();
        service.runPulseForTest();
        Thread.sleep(30L);
        service.runPulseForTest();

        assertEquals(List.of("start-1", "end-1"), service.events());
    }

    @Test
    void locationForwardRequestShouldReturnActorNotFoundWhenQueuedActorBecomesMissing() throws Exception {
        TestNode node = new TestNode(nextAddr());
        TestActorService target = new TestActorService(node, "target-missing");
        TestRequesterService requester = new TestRequesterService(node, "requester-missing");
        node.addService(target);
        node.addService(requester);

        ActorId actorId = ActorId.player(7007L);
        target.registerActorForTest(actorId, new Object(), ActorExecutionMode.ORDERED);
        target.scheduleAfterSleepHook(1, () -> target.unregisterActorForTest(actorId));

        target.addActorCall(actorId, 1, 20L);
        requester.requestLocationCall(new CallPoint(node.getId(), target.getId()), actorId, 2, 0L);

        target.runPulseForTest();
        requester.runPulseForTest();
        target.runPulseForTest();
        Thread.sleep(30L);
        target.runPulseForTest();
        requester.runPulseForTest();

        RpcCallException exception = requester.getLastFailure();
        assertNotNull(exception);
        assertEquals(RpcErrorCodes.ACTOR_NOT_FOUND, exception.getErrorCode());
        assertEquals(List.of("start-1", "end-1"), target.events());
        assertNull(requester.getLastResult());
    }

    @Test
    void locationForwardRequestShouldReturnActorNotFoundWhenActorReRegistersAfterQueueing() throws Exception {
        TestNode node = new TestNode(nextAddr());
        TestActorService target = new TestActorService(node, "target-reregister");
        TestRequesterService requester = new TestRequesterService(node, "requester-reregister");
        node.addService(target);
        node.addService(requester);

        ActorId actorId = ActorId.player(8008L);
        target.registerActorForTest(actorId, new Object(), ActorExecutionMode.ORDERED);
        target.scheduleAfterSleepHook(1, () -> {
            target.unregisterActorForTest(actorId);
            target.registerActorForTest(actorId, new Object(), ActorExecutionMode.ORDERED);
        });

        target.addActorCall(actorId, 1, 20L);
        requester.requestLocationCall(new CallPoint(node.getId(), target.getId()), actorId, 2, 0L);

        target.runPulseForTest();
        requester.runPulseForTest();
        target.runPulseForTest();
        Thread.sleep(30L);
        target.runPulseForTest();
        requester.runPulseForTest();

        RpcCallException exception = requester.getLastFailure();
        assertNotNull(exception);
        assertEquals(RpcErrorCodes.ACTOR_NOT_FOUND, exception.getErrorCode());
        assertEquals(List.of("start-1", "end-1"), target.events());
        assertNull(requester.getLastResult());
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

    static final class TestActorService extends Service {
        static final int METHOD_SLEEP_AND_RECORD = 1;

        private final List<String> events = new ArrayList<>();
        private final Map<Integer, Runnable> afterSleepHooks = new HashMap<>();

        TestActorService(Node node, String name) {
            super(node, name, "test-scheduled");
        }

        void registerActorForTest(ActorId actorId, Object actor, ActorExecutionMode executionMode) {
            registerActor(actorId, actor, executionMode);
        }

        void unregisterActorForTest(ActorId actorId) {
            unregisterActor(actorId);
        }

        void scheduleAfterSleepHook(int marker, Runnable hook) {
            afterSleepHooks.put(marker, hook);
        }

        void addActorCall(ActorId actorId, int marker, long sleepMillis) {
            addCall_snt(newActorCall(actorId, METHOD_SLEEP_AND_RECORD, new Object[]{marker, sleepMillis}));
        }

        Call newActorCall(ActorId actorId, int methodKey, Object[] params) {
            Call call = new Call();
            call.from = new CallPoint("test-node", "from");
            call.to = new CallPoint(node.getId(), getId());
            call.actorId = new ActorId(actorId);
            call.methodKey = methodKey;
            call.methodParam = params;
            return call;
        }

        Call newForwardActorCall(ActorId actorId, int methodKey, Object[] params) {
            return ActorForwarding.createMessageEnvelope(
                    new CallPoint("test-node", "from"),
                    new CallPoint(node.getId(), getId()),
                    actorId,
                    methodKey,
                    params);
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
            Runnable hook = afterSleepHooks.remove(marker);
            if (hook != null) {
                hook.run();
            }
            events.add("end-" + marker);
        }

        @Override
        public void tick() {
        }

        @Override
        public void init() {
        }
    }

    static final class TestRequesterService extends Service {
        private Runnable tickAction;
        private Object lastResult;
        private RpcCallException lastFailure;

        TestRequesterService(Node node, String name) {
            super(node, name, "test-scheduled");
        }

        void requestLocationCall(CallPoint target, ActorId actorId, int marker, long sleepMillis) {
            this.lastResult = null;
            this.lastFailure = null;
            this.tickAction = () -> {
                try {
                    lastResult = locationCallWait(target, actorId, TestActorService.METHOD_SLEEP_AND_RECORD, new Object[]{marker, sleepMillis});
                } catch (RpcCallException e) {
                    lastFailure = e;
                }
            };
        }

        Object getLastResult() {
            return lastResult;
        }

        RpcCallException getLastFailure() {
            return lastFailure;
        }

        void runPulseForTest() {
            pulseCase_t();
        }

        @Override
        public void tick() {
            Runnable action = tickAction;
            tickAction = null;
            if (action != null) {
                action.run();
            }
        }

        @Override
        public void init() {
        }
    }

    public static final class TestActorServiceImpl extends RPCImplBase {
        @Override
        public Object getMethodFunction(Service service, int methodKey) {
            TestActorService actorService = (TestActorService) service;
            if (methodKey == TestActorService.METHOD_SLEEP_AND_RECORD) {
                return (org.evd.game.runtime.support.function.Function2<Integer, Long>) actorService::sleepAndRecord;
            }
            throw new IllegalArgumentException("unknown methodKey: " + methodKey);
        }
    }
}
