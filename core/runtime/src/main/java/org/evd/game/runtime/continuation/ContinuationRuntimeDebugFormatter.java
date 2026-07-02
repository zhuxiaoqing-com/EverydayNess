package org.evd.game.runtime.continuation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

final class ContinuationRuntimeDebugFormatter {
    private ContinuationRuntimeDebugFormatter() {
    }

    static String buildDebugDump(Task.ContinuationWrapper runningContinuation,
                                 SnapshotSection readyContinuations,
                                 SnapshotSection waitingContinuations,
                                 SnapshotSection heldContinuations) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("协程运行时快照:\n");
        appendRunningContinuation(sb, runningContinuation);
        appendContinuationGroup(sb, readyContinuations);
        appendContinuationGroup(sb, waitingContinuations);
        appendContinuationGroup(sb, heldContinuations);
        return sb.toString();
    }

    private static void appendRunningContinuation(StringBuilder sb, Task.ContinuationWrapper runningContinuation) {
        sb.append("  运行中协程:\n");
        if (runningContinuation == null) {
            sb.append("    none\n");
            return;
        }
        sb.append("    conId=").append(runningContinuation.getConId()).append(", ")
                .append(buildDebugSummary(runningContinuation)).append('\n');
        appendStack(sb, "      ", "mounted线程栈", runningContinuation.getDebugStackTrace());
    }

    static SnapshotSection section(String title,
                                   List<Task.ContinuationWrapper> continuations,
                                   RuntimeException snapshotFailure) {
        return new SnapshotSection(title, continuations, snapshotFailure);
    }

    private static void appendContinuationGroup(StringBuilder sb, SnapshotSection section) {
        sb.append(section.title);
        if (section.snapshotFailure != null) {
            sb.append("    snapshot failed: ").append(section.snapshotFailure).append('\n');
            return;
        }
        if (section.continuations.isEmpty()) {
            sb.append("    none\n");
            return;
        }
        appendGroupedContinuations(sb, "    ", section.continuations);
    }

    private static void appendGroupedContinuations(StringBuilder sb,
                                                   String indent,
                                                   List<Task.ContinuationWrapper> continuations) {
        appendDebugInfoGroups(sb, indent, continuations);
        appendStackGroups(sb, indent, continuations);
    }

    private static void appendDebugInfoGroups(StringBuilder sb,
                                              String indent,
                                              List<Task.ContinuationWrapper> continuations) {
        sb.append(indent).append("debugInfo聚合:\n");
        Map<String, ContinuationDebugGroup> groups = new LinkedHashMap<>();
        for (Task.ContinuationWrapper continuation : continuations) {
            String summary = buildDebugSummary(continuation);
            ContinuationDebugGroup group = groups.get(summary);
            if (group == null) {
                group = new ContinuationDebugGroup(summary);
                groups.put(summary, group);
            }
            group.continuations.add(continuation);
        }
        for (ContinuationDebugGroup group : groups.values()) {
            sb.append(indent)
                    .append("  count=").append(group.continuations.size())
                    .append(", ").append(group.summary)
                    .append('\n');
            appendSampleIds(sb, indent + "    ", group.continuations);
        }
    }

    private static void appendStackGroups(StringBuilder sb,
                                          String indent,
                                          List<Task.ContinuationWrapper> continuations) {
        sb.append(indent).append("堆栈聚合:\n");
        Map<String, ContinuationStackGroup> groups = new LinkedHashMap<>();
        for (Task.ContinuationWrapper continuation : continuations) {
            StackTraceElement[] debugStack = continuation.getDebugStackTrace();
            boolean stackAvailable = debugStack.length > 0;
            String unavailableReason = buildUnavailableReason(continuation);
            String signature = stackAvailable ? buildStackSignature(debugStack) : unavailableReason;
            ContinuationStackGroup group = groups.get(signature);
            if (group == null) {
                group = new ContinuationStackGroup(stackAvailable, debugStack, unavailableReason);
                groups.put(signature, group);
            }
            group.continuations.add(continuation);
        }
        for (ContinuationStackGroup group : groups.values()) {
            appendStackGroup(sb, indent + "  ", group);
        }
    }

    private static void appendStackGroup(StringBuilder sb, String indent, ContinuationStackGroup group) {
        Task.ContinuationWrapper sample = group.continuations.getFirst();
        sb.append(indent)
                .append("count=").append(group.continuations.size())
                .append(", sample=")
                .append(buildDebugSummary(sample))
                .append('\n');
        if (group.stackAvailable) {
            appendStack(sb, indent + "  ", "continuation挂起栈", group.stack);
        } else {
            sb.append(indent)
                    .append("  协程栈: unavailable")
                    .append(" (").append(group.unavailableReason).append(")")
                    .append('\n');
        }
        appendSampleIds(sb, indent + "  ", group.continuations);
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

    private static String buildDebugSummary(Task.ContinuationWrapper continuation) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("state=").append(continuation.getDebugState())
                .append(", queueReason=").append(continuation.getQueueReason());
        appendCombinedDebugInfo(sb, continuation);
        return sb.toString();
    }

    private static String buildUnavailableReason(Task.ContinuationWrapper continuation) {
        StringBuilder sb = new StringBuilder(128);
        sb.append(buildDebugSummary(continuation));
        sb.append(", 当前没有可用栈帧（可能尚未运行、已经执行完成，或当前状态未保留可见 Java 栈）");
        return sb.toString();
    }

    private static void appendCombinedDebugInfo(StringBuilder sb,
                                                Task.ContinuationWrapper continuation) {
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

    private static final class ContinuationDebugGroup {
        private final String summary;
        private final List<Task.ContinuationWrapper> continuations = new ArrayList<>();

        private ContinuationDebugGroup(String summary) {
            this.summary = summary;
        }
    }

    private static final class ContinuationStackGroup {
        private final boolean stackAvailable;
        private final StackTraceElement[] stack;
        private final String unavailableReason;
        private final List<Task.ContinuationWrapper> continuations = new ArrayList<>();

        private ContinuationStackGroup(boolean stackAvailable,
                                       StackTraceElement[] stack,
                                       String unavailableReason) {
            this.stackAvailable = stackAvailable;
            this.stack = stack;
            this.unavailableReason = unavailableReason;
        }
    }

    static final class SnapshotSection {
        private final String title;
        private final List<Task.ContinuationWrapper> continuations;
        private final RuntimeException snapshotFailure;

        private SnapshotSection(String title,
                                List<Task.ContinuationWrapper> continuations,
                                RuntimeException snapshotFailure) {
            this.title = title;
            this.continuations = continuations;
            this.snapshotFailure = snapshotFailure;
        }
    }
}
