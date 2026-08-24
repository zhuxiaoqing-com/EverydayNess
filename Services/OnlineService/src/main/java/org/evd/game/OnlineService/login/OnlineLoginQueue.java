package org.evd.game.OnlineService.login;

import org.evd.game.common.serializeBean.OnlineService.login.OnlineLoginAdmission;
import org.evd.game.runtime.call.CallPoint;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Online 登录 FIFO 队列，只负责排队、替换、取消和按速率放行。 */
public final class OnlineLoginQueue {
    /** 保存排队登录请求及其来源网关会话。 */
    public record QueuedLogin(String userId, CallPoint gate, long sessionId) {
        /** 复制网关地址，避免调用方修改队列中的请求来源。 */
        public QueuedLogin {
            gate = new CallPoint(gate);
        }
    }

    private final int admissionsPerSecond;
    private final int maxQueueSize;
    private final ArrayDeque<QueuedLogin> requests = new ArrayDeque<>();
    private final Map<String, QueuedLogin> requestsByUser = new HashMap<>();
    private long releaseSecond = -1L;
    private int releasedThisSecond;

    /** 创建带速率限制和容量上限的登录队列。 */
    public OnlineLoginQueue(int admissionsPerSecond, int maxQueueSize) {
        this.admissionsPerSecond = admissionsPerSecond;
        this.maxQueueSize = maxQueueSize;
    }

    /** 将登录请求加入队尾，必要时替换同一用户的旧请求。 */
    public boolean offer(String userId, CallPoint gate, long sessionId,
                         OnlineLoginCoordinator login) {
        QueuedLogin old = requestsByUser.remove(userId);
        if (old != null) {
            requests.remove(old);
            login.onReplaced(old);
        } else if (requests.size() >= maxQueueSize) {
            return false;
        }
        QueuedLogin request = new QueuedLogin(userId, gate, sessionId);
        requestsByUser.put(userId, request);
        requests.add(request);
        return true;
    }

    /** 按用户、网关和会话号取消指定排队请求。 */
    public boolean cancel(String userId, CallPoint gate, long sessionId) {
        QueuedLogin request = requestsByUser.get(userId);
        if (request == null || gate == null || !gate.equals(request.gate())
                || sessionId != request.sessionId()) {
            return false;
        }
        requestsByUser.remove(userId, request);
        requests.remove(request);
        return true;
    }

    /** 返回用户当前在队列中的位置。 */
    public int position(String userId) {
        int position = 1;
        for (QueuedLogin request : requests) {
            if (request.userId().equals(userId)) {
                return position;
            }
            position++;
        }
        return position;
    }

    /** 返回当前排队请求数量。 */
    public int size() {
        return requests.size();
    }

    /** 按当前秒的放行额度处理队首请求。 */
    public void pump(long now, OnlineLoginCoordinator login) {
        long nowSecond = now / 1_000L;
        if (nowSecond != releaseSecond) {
            releaseSecond = nowSecond;
            releasedThisSecond = 0;
        }
        int releaseBudget = admissionsPerSecond - releasedThisSecond;
        while (releaseBudget > 0 && !requests.isEmpty()) {
            QueuedLogin request = requests.peek();
            if (!login.canAdmit(request.userId())) {
                return;
            }
            requests.poll();
            requestsByUser.remove(request.userId(), request);
            OnlineLoginAdmission admission = login.createAdmission(request.userId(), now);
         /*   if (admission == null || admission.getTokenState() == null) {
                requests.addFirst(request);
                requestsByUser.put(request.userId(), request);
                return;
            }*/
            releasedThisSecond++;
            releaseBudget--;
            login.onAdmissionReady(request, admission);
        }
    }
}
