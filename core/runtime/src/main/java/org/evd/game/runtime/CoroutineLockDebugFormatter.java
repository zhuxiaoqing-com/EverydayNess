package org.evd.game.runtime;

import org.evd.game.runtime.continuation.ContinuationDebugInfo;
import org.evd.game.runtime.continuation.LockType;
import org.evd.game.runtime.continuation.Task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

final class CoroutineLockDebugFormatter {
    private CoroutineLockDebugFormatter() {
    }

    static Snapshot snapshot(List<LockSnapshot> locks, RuntimeException snapshotFailure) {
        return new Snapshot(locks, snapshotFailure);
    }

    static LockSnapshot lockSnapshot(LockType type,
                                     Object key,
                                     Task.ContinuationWrapper owner,
                                     List<Task.ContinuationWrapper> waiters) {
        return new LockSnapshot(type, key, owner, waiters);
    }

    static String buildDebugDump(Snapshot snapshot) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("协程锁快照:\n");
        if (snapshot.snapshotFailure != null) {
            sb.append("  snapshot failed: ").append(snapshot.snapshotFailure).append('\n');
            return sb.toString();
        }
        if (snapshot.locks.isEmpty()) {
            sb.append("  none\n");
            return sb.toString();
        }
        for (LockSnapshot lock : snapshot.locks) {
            sb.append("  lock[type=").append(lock.type)
                    .append(", key=").append(lock.key)
                    .append("]\n");
            if (lock.owner == null) {
                sb.append("    owner: none\n");
            } else {
                sb.append("    owner:\n");
                appendContinuation(sb, "      ", lock.owner, "owns lock");
            }

            if (lock.waiters.isEmpty()) {
                sb.append("    waiters: none\n");
                continue;
            }

            sb.append("    waiters:\n");
            appendGroupedContinuations(sb, "      ", lock.waiters);
        }
        return sb.toString();
    }

    private static void appendGroupedContinuations(StringBuilder sb,
                                                   String indent,
                                                   List<Task.ContinuationWrapper> waiters) {
        sb.append(indent).append("debugInfo聚合:\n");
        Map<String, LockDebugGroup> debugGroups = new LinkedHashMap<>();
        for (Task.ContinuationWrapper continuation : waiters) {
            String summary = buildDebugSummary(continuation);
            LockDebugGroup debugGroup = debugGroups.get(summary);
            if (debugGroup == null) {
                debugGroup = new LockDebugGroup(summary);
                debugGroups.put(summary, debugGroup);
            }
            debugGroup.continuations.add(continuation);
        }
        for (LockDebugGroup debugGroup : debugGroups.values()) {
            sb.append(indent)
                    .append("  count=").append(debugGroup.continuations.size())
                    .append(", ").append(debugGroup.summary)
                    .append('\n');
            appendSampleIds(sb, indent + "    ", debugGroup.continuations);
        }

        sb.append(indent).append("堆栈聚合:\n");
        Map<String, LockStackGroup> stackGroups = new LinkedHashMap<>();
        for (Task.ContinuationWrapper continuation : waiters) {
            StackTraceElement[] debugStack = continuation.getDebugStackTrace();
            boolean stackAvailable = debugStack.length > 0;
            String unavailableReason = buildUnavailableReason(continuation);
            String signature = stackAvailable ? buildStackSignature(debugStack) : unavailableReason;
            LockStackGroup stackGroup = stackGroups.get(signature);
            if (stackGroup == null) {
                stackGroup = new LockStackGroup(stackAvailable, debugStack, unavailableReason);
                stackGroups.put(signature, stackGroup);
            }
            stackGroup.continuations.add(continuation);
        }
        for (LockStackGroup stackGroup : stackGroups.values()) {
            appendStackGroup(sb, indent + "  ", stackGroup);
        }
    }

    private static void appendStackGroup(StringBuilder sb, String indent, LockStackGroup stackGroup) {
        Task.ContinuationWrapper sample = stackGroup.continuations.getFirst();
        sb.append(indent)
                .append("count=").append(stackGroup.continuations.size())
                .append(", sample=")
                .append(buildDebugSummary(sample))
                .append('\n');
        if (stackGroup.stackAvailable) {
            appendStack(sb, indent + "  ", "continuation挂起栈", stackGroup.stack);
        } else {
            sb.append(indent)
                    .append("  协程栈: unavailable")
                    .append(" (").append(stackGroup.unavailableReason).append(")")
                    .append('\n');
        }
        appendSampleIds(sb, indent + "  ", stackGroup.continuations);
    }

    private static void appendSampleIds(StringBuilder sb,
                                        String indent,
                                        List<Task.ContinuationWrapper> continuations) {
        StringJoiner joiner = new StringJoiner(", ");
        int limit = Math.min(continuations.size(), 8);
        for (int i = 0; i < limit; i++) {
            joiner.add(Long.toString(continuations.get(i).getConId()));
        }
        if (continuations.size() > limit) {
            joiner.add("...");
        }
        sb.append(indent).append("sampleConIds=").append(joiner).append('\n');
    }

    private static void appendContinuation(StringBuilder sb,
                                           String indent,
                                           Task.ContinuationWrapper continuation,
                                           String extra) {
        sb.append(indent)
                .append("conId=").append(continuation.getConId())
                .append(", ").append(buildDebugSummary(continuation));
        if (extra != null && !extra.isBlank()) {
            sb.append(", ").append(extra);
        }
        sb.append('\n');
        StackTraceElement[] stack = continuation.getDebugStackTrace();
        if (stack.length > 0) {
            String stackTitle = continuation.getDebugState() == Task.DebugState.RUNNING
                    ? "mounted线程栈"
                    : "continuation挂起栈";
            appendStack(sb, indent + "  ", stackTitle, stack);
        } else {
            sb.append(indent)
                    .append("  协程栈: unavailable")
                    .append(" (").append(buildUnavailableReason(continuation)).append(")")
                    .append('\n');
        }
    }

    private static String buildDebugSummary(Task.ContinuationWrapper continuation) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("state=").append(continuation.getDebugState())
                .append(", queueReason=").append(continuation.getQueueReason());
        appendCombinedDebugInfo(sb, continuation);
        return sb.toString();
    }

    private static String buildUnavailableReason(Task.ContinuationWrapper continuation) {
        return buildDebugSummary(continuation)
                + ", 当前没有可用栈帧（可能尚未运行、已经执行完成，或当前状态未保留可见 Java 栈）";
    }

    private static void appendCombinedDebugInfo(StringBuilder sb, Task.ContinuationWrapper continuation) {
        ContinuationDebugInfo.DebugInfo sourceDebugInfo = continuation.getDebugInfo();
        ContinuationDebugInfo.DebugInfo waitDebugInfo = continuation.getWaitDebugInfo();
        if (sourceDebugInfo == null && waitDebugInfo == null) {
            return;
        }
        sb.append(", debugInfo=");
        if (sourceDebugInfo != null) {
            sb.append("source[").append(sourceDebugInfo).append(']');
        }
        if (waitDebugInfo != null) {
            if (sourceDebugInfo != null) {
                sb.append(", ");
            }
            sb.append("wait[").append(waitDebugInfo).append(']');
        }
    }

    private static String buildStackSignature(StackTraceElement[] stack) {
        StringJoiner joiner = new StringJoiner("\n");
        for (StackTraceElement element : stack) {
            joiner.add(element.toString());
        }
        return joiner.toString();
    }

    private static void appendStack(StringBuilder sb, String indent, String title, StackTraceElement[] stack) {
        sb.append(indent).append(title).append(":\n");
        if (stack == null || stack.length == 0) {
            sb.append(indent).append("  none\n");
            return;
        }
        for (StackTraceElement element : stack) {
            sb.append(indent).append("  at ").append(element).append('\n');
        }
    }

    private static final class LockDebugGroup {
        private final String summary;
        private final List<Task.ContinuationWrapper> continuations = new ArrayList<>();

        private LockDebugGroup(String summary) {
            this.summary = summary;
        }
    }

    private static final class LockStackGroup {
        private final boolean stackAvailable;
        private final StackTraceElement[] stack;
        private final String unavailableReason;
        private final List<Task.ContinuationWrapper> continuations = new ArrayList<>();

        private LockStackGroup(boolean stackAvailable,
                               StackTraceElement[] stack,
                               String unavailableReason) {
            this.stackAvailable = stackAvailable;
            this.stack = stack;
            this.unavailableReason = unavailableReason;
        }
    }

    static final class Snapshot {
        private final List<LockSnapshot> locks;
        private final RuntimeException snapshotFailure;

        private Snapshot(List<LockSnapshot> locks, RuntimeException snapshotFailure) {
            this.locks = locks;
            this.snapshotFailure = snapshotFailure;
        }
    }

    static final class LockSnapshot {
        private final LockType type;
        private final Object key;
        private final Task.ContinuationWrapper owner;
        private final List<Task.ContinuationWrapper> waiters;

        private LockSnapshot(LockType type,
                             Object key,
                             Task.ContinuationWrapper owner,
                             List<Task.ContinuationWrapper> waiters) {
            this.type = type;
            this.key = key;
            this.owner = owner;
            this.waiters = waiters;
        }
    }
}
