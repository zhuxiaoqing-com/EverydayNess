package org.evd.game.runtime.util;

import org.apache.logging.log4j.message.ParameterizedMessage;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author zhuxiaoqing
 * @Description: RuntimeUtils
 * @Date 2026/6/26 10:19
 **/
public class RuntimeUtils {

    /**
     * 基于参数创建字符串
     * #0开始
     *
     * @param str
     * @param params
     * @return
     */
    public static String createStr(String str, Object... params) {
        return ParameterizedMessage.format(str, params);
    }

    public static String formatMegabytes(long bytes) {
        return String.format(Locale.ROOT, "%.2f", bytes / (1024.0d * 1024.0d));
    }

    public static <K, V> Map<K, List<V>> convertModifyListMap(Map<K, List<V>> map) {
        map.replaceAll((k, v) -> Collections.unmodifiableList(v));
        return map;
    }


    public static <K> K mod(Object key, List<K> list) {
        int length = list.size();
        if (length <= 0) {
            return null;
        }
        int i = mod(key, length);
        return list.get(i);
    }

    public static <K> K mod(Object key, K[] list) {
        int length = list.length;
        if (length <= 0) {
            return null;
        }
        int i = mod(key, length);
        return list[i];
    }

    public static int mod(Object key, int length) {
        return Math.floorMod(hash(key), length);
    }


    public static int hash(Object key) {
        int h;
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }

}
