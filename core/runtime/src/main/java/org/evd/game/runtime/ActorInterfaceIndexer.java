package org.evd.game.runtime;

import org.evd.game.runtime.event.Event;
import org.evd.game.runtime.event.EventListener;
import org.evd.game.runtime.event.GameEvent;
import org.evd.game.runtime.support.SysException;

import java.util.*;

/**
 * 基于 actor 具体类型，建立“直接接口 -> actor 实例”的只读索引。
 */
public final class ActorInterfaceIndexer {
    private final Map<Class<?>, List<Object>> interfaceActors;

    public ActorInterfaceIndexer(Map<Class<?>, Object> actors) {
        this.interfaceActors = buildIndex(actors);
    }

    public Map<Class<?>, List<Object>> getInterfaceActors() {
        return interfaceActors;
    }

    private Map<Class<?>, List<Object>> buildIndex(Map<Class<?>, Object> actors) {
        // 先保留 actor 类型，后面事件监听排序要按注解里的 dependsOn 关系处理。
        Map<Class<?>, List<ActorBinding>> interfaceBindings = new LinkedHashMap<>();
        if (actors == null || actors.isEmpty()) {
            return Collections.emptyMap();
        }
        for (Map.Entry<Class<?>, Object> entry : actors.entrySet()) {
            Class<?> actorType = entry.getKey();
            Object actor = entry.getValue();
            if (actorType == null || actor == null) {
                continue;
            }
            for (Class<?> actorInterface : actorType.getInterfaces()) {
                interfaceBindings.computeIfAbsent(actorInterface, a -> new ArrayList<>())
                        .add(new ActorBinding(actorType, actor));
            }
        }
        Map<Class<?>, List<Object>> interfaceIndex = new LinkedHashMap<>();
        for (Map.Entry<Class<?>, List<ActorBinding>> entry : interfaceBindings.entrySet()) {
            Class<?> actorInterface = entry.getKey();
            List<ActorBinding> bindings = entry.getValue();
            if (EventListener.class.isAssignableFrom(actorInterface)) {
                // 事件监听接口需要按依赖拓扑排序，普通接口保持原始注册顺序即可。
                interfaceIndex.put(actorInterface, sortEventActors(actorInterface, bindings));
                continue;
            }
            interfaceIndex.put(actorInterface, bindings.stream().map(ActorBinding::actor).toList());
        }
        return interfaceIndex;
    }

    // 事件监听器按 @GameEvent.dependsOn 做拓扑排序，避免事件初始化顺序不稳定。
    private List<Object> sortEventActors(Class<?> eventListenerType, List<ActorBinding> bindings) {
        // actorClass -> actor 绑定。排序过程中只操作类型，最终再回填成真正的 actor 实例。
        Map<Class<?>, ActorBinding> bindingByActorType = new LinkedHashMap<>();
        // actorClass -> 原始注册顺序。多个节点同时可执行时，用它保证结果稳定，不受 Hash 顺序影响。
        Map<Class<?>, Integer> orderIndex = new LinkedHashMap<>();
        // 邻接表：dependency -> [依赖它之后才能执行的 actorClass]。
        // 例如 A 被 B dependsOn，则这里记录 A -> B。
        Map<Class<?>, Set<Class<?>>> edges = new LinkedHashMap<>();
        // 入度表：actorClass 还有多少前置依赖未满足。
        // 入度为 0 代表当前节点已经可以排进最终顺序。
        Map<Class<?>, Integer> indegree = new LinkedHashMap<>();
        // actorClass -> 它在当前 listener 下声明的 @GameEvent 配置。
        // 这里提前解析好，避免后面建图时重复扫注解。
        Map<Class<?>, GameEvent> configByActorType = new LinkedHashMap<>();

        for (int i = 0; i < bindings.size(); i++) {
            ActorBinding binding = bindings.get(i);
            bindingByActorType.put(binding.actorClass(), binding);
            // 没有依赖约束时，仍然尽量保持原始注册顺序稳定。
            orderIndex.put(binding.actorClass(), i);
            edges.put(binding.actorClass(), new LinkedHashSet<>());
            indegree.put(binding.actorClass(), 0);
            configByActorType.put(binding.actorClass(), resolveEventConfig(binding.actorClass(), eventListenerType));
        }

        // 第一阶段：根据 dependsOn 建图。
        // 如果 current dependsOn dependency，图里连一条 dependency -> current 的边。
        // 这样做的好处是：谁先执行、谁后执行，能直接用入度递减表达。
        for (ActorBinding binding : bindings) {
            GameEvent config = configByActorType.get(binding.actorClass());
            if (config == null) {
                // 没写 @GameEvent 的 actor 不参与依赖排序，保留默认顺序即可。
                continue;
            }
            for (Class<?> dependencyType : config.dependsOn()) {
                ActorBinding dependencyBinding = bindingByActorType.get(dependencyType);
                if (dependencyBinding == null) {
                    throw new SysException("事件监听依赖未注册: listener={}, actor={}, dependsOn={}",
                            eventListenerType.getName(),
                            binding.actorClass().getName(),
                            dependencyType.getName());
                }
                if (edges.get(dependencyType).add(binding.actorClass())) {
                    // dependency -> current，表示 current 必须排在 dependency 后面。
                    indegree.put(binding.actorClass(), indegree.get(binding.actorClass()) + 1);
                }
            }
        }

        // 第二阶段：初始化可执行队列。
        // Kahn 拓扑排序要求先把所有入度为 0 的点放进队列。
        // 这里用优先队列而不是普通队列，是为了在“都可执行”的情况下仍按原始注册顺序取。
        PriorityQueue<Class<?>> ready = new PriorityQueue<>(Comparator.comparingInt(orderIndex::get));
        for (ActorBinding binding : bindings) {
            if (indegree.get(binding.actorClass()) == 0) {
                ready.offer(binding.actorClass());
            }
        }

        List<Object> sortedActors = new ArrayList<>(bindings.size());
        while (!ready.isEmpty()) {
            Class<?> actorType = ready.poll();
            // 当前节点入度为 0，说明它的前置依赖都已经排完，可以安全输出。
            sortedActors.add(bindingByActorType.get(actorType).actor());
            for (Class<?> nextActorType : edges.get(actorType)) {
                // 当前节点已经输出，相当于“消耗掉”一条 dependency -> next 的边，
                // 所以下游节点的未满足依赖数减 1。
                int nextIndegree = indegree.get(nextActorType) - 1;
                indegree.put(nextActorType, nextIndegree);
                if (nextIndegree == 0) {
                    // 下游节点所有依赖都满足后，加入待执行队列。
                    ready.offer(nextActorType);
                }
            }
        }

        if (sortedActors.size() != bindings.size()) {
            // 还有节点永远没法入队，说明它们互相卡住了，也就是依赖图里存在闭环。
            // 这里把仍然入度 > 0 的 actor 打出来，方便直接定位冲突对象。
            List<String> cycleActors = new ArrayList<>();
            for (ActorBinding binding : bindings) {
                if (indegree.get(binding.actorClass()) > 0) {
                    cycleActors.add(binding.actorClass().getName());
                }
            }
            throw new SysException("事件监听依赖存在环: listener={}, actors={}",
                    eventListenerType.getName(),
                    String.join(" -> ", cycleActors));
        }
        return sortedActors;
    }

    private GameEvent resolveEventConfig(Class<?> actorType, Class<?> eventListenerType) {
        GameEvent matched = null;
        for (GameEvent annotation : actorType.getAnnotationsByType(GameEvent.class)) {
            Class<?> annotationListenerType = resolveEventListenerType(annotation.event());
            if (!eventListenerType.equals(annotationListenerType)) {
                continue;
            }
            if (matched != null) {
                throw new SysException("事件监听配置重复: actor={}, listener={}, event={}",
                        actorType.getName(),
                        eventListenerType.getName(),
                        annotation.event().getName());
            }
            matched = annotation;
        }
        return matched;
    }

    // @GameEvent 面向业务声明 event 类型，这里负责把 event 解析成真正参与索引的 Listener 接口。
    private Class<?> resolveEventListenerType(Class<? extends Event> eventType) {
        Class<?> matched = null;
        for (Class<?> declaredClass : eventType.getDeclaredClasses()) {
            // 事件内部可能还有别的内部类，这里只认监听器接口。
            if (!EventListener.class.isAssignableFrom(declaredClass)) {
                continue;
            }
            if (matched != null) {
                throw new SysException("事件定义存在多个监听接口: event={}", eventType.getName());
            }
            matched = declaredClass;
        }
        if (matched == null) {
            throw new SysException("事件定义缺少监听接口: event={}", eventType.getName());
        }
        return matched;
    }

    public List<Object> getObjByClass(Class<?> clazz) {
        return interfaceActors.getOrDefault(clazz, Collections.emptyList());
    }

    private record ActorBinding(Class<?> actorClass, Object actor) {
    }
}
