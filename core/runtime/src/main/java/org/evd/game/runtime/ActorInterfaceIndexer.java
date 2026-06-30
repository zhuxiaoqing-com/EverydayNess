package org.evd.game.runtime;

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
        Map<Class<?>, List<Object>> interfaceIndex = new LinkedHashMap<>();
        if (actors == null || actors.isEmpty()) {
            return interfaceIndex;
        }
        for (Map.Entry<Class<?>, Object> entry : actors.entrySet()) {
            Class<?> actorType = entry.getKey();
            Object actor = entry.getValue();
            if (actorType == null || actor == null) {
                continue;
            }
            for (Class<?> actorInterface : actorType.getInterfaces()) {
                interfaceIndex.computeIfAbsent(actorInterface, a -> new ArrayList<>()).add(actor);
            }
        }
        return interfaceIndex;
    }


    public List<Object> getObjByClass(Class<?> clazz) {
        return interfaceActors.getOrDefault(clazz, Collections.emptyList());
    }
}
