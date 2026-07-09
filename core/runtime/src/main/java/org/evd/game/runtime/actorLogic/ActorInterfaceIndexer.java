package org.evd.game.runtime.actorLogic;

import java.util.*;

/**
 * 基于 actor 具体类型，建立“直接接口 -> actor 实例”的只读索引。
 */
public final class ActorInterfaceIndexer {
    private final Map<Class<?>, List<ActorInterfaceBinding>> interfaceBindings;
    private Map<Class<?>, List<Object>> interfaceActors;

    public ActorInterfaceIndexer(Map<Class<?>, Object> actors) {
        this.interfaceBindings = buildBindings(actors);
        this.interfaceActors = Collections.emptyMap();
    }

    public Map<Class<?>, List<Object>> getInterfaceActors() {
        return interfaceActors;
    }

    private Map<Class<?>, List<ActorInterfaceBinding>> buildBindings(Map<Class<?>, Object> actors) {
        // 第一步：只做最原始的接口归类，不在这里掺杂事件排序逻辑。
        Map<Class<?>, List<ActorInterfaceBinding>> interfaceBindings = new LinkedHashMap<>();
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
                        .add(new ActorInterfaceBinding(actorType, actor));
            }
        }
        return interfaceBindings;
    }

    @SuppressWarnings({"unchecked"})
    public <T> List<T> getObjByClass(Class<T> clazz) {
        return (List<T>) interfaceActors.getOrDefault(clazz, Collections.emptyList());
    }


    public Map<Class<?>, List<ActorInterfaceBinding>> getInterfaceBindings() {
        return interfaceBindings;
    }

    public void setInterfaceActors(Map<Class<?>, List<Object>> interfaceActors) {
        this.interfaceActors = interfaceActors;
    }

}
