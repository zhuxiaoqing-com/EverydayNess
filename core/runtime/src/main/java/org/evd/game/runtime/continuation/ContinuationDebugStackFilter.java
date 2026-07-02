package org.evd.game.runtime.continuation;

final class ContinuationDebugStackFilter {
    private static final int DEBUG_STACK_LIMIT = 24;
    private static final StackTraceElement[] EMPTY_STACK = new StackTraceElement[0];

    private ContinuationDebugStackFilter() {
    }

    static StackTraceElement[] filter(StackTraceElement[] source) {
        if (source == null || source.length == 0) {
            return EMPTY_STACK;
        }

        StackTraceElement[] filtered = new StackTraceElement[Math.min(DEBUG_STACK_LIMIT, source.length)];
        int count = 0;
        for (StackTraceElement element : source) {
            if (shouldSkipFrame(element)) {
                continue;
            }
            filtered[count++] = element;
            if (count >= DEBUG_STACK_LIMIT) {
                break;
            }
        }
        if (count == 0) {
            return EMPTY_STACK;
        }
        StackTraceElement[] result = new StackTraceElement[count];
        System.arraycopy(filtered, 0, result, 0, count);
        return result;
    }

    static StackTraceElement[] emptyStack() {
        return EMPTY_STACK;
    }

    private static boolean shouldSkipFrame(StackTraceElement element) {
        String className = element.getClassName();
        if (Thread.class.getName().equals(className)) {
            return true;
        }
        if ("jdk.internal.vm.Continuation".equals(className)) {
            return true;
        }
        return className.equals(Task.class.getName())
                || className.equals(Task.ContinuationWrapper.class.getName())
                || className.equals("org.evd.game.runtime.continuation.ContinuationRuntime")
                || className.equals("org.evd.game.runtime.CoroutineLockManager")
                || className.equals("org.evd.game.runtime.RpcOutboundGateway")
                || className.equals("org.evd.game.runtime.MessageSender")
                || className.equals("org.evd.game.runtime.Service");
    }
}
