package org.evd.game.common;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Utils {

    public static <K, V> void convertModifyListMap(Map<K, List<V>> map) {
        map.replaceAll((k, v) -> Collections.unmodifiableList(v));
    }
}
