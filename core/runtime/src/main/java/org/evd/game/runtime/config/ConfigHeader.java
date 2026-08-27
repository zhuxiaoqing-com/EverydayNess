package org.evd.game.runtime.config;

import java.util.HashMap;
import java.util.Map;

/** CSV 表头，在读取数据行前只建立一次列索引。 */
public final class ConfigHeader {
    private final Map<String, Integer> indexes;

    private ConfigHeader(Map<String, Integer> indexes) {
        this.indexes = indexes;
    }

    public static ConfigHeader of(Map<String, Integer> indexes) {
        return new ConfigHeader(Map.copyOf(indexes));
    }

    public int require(String column, String file, String config, String field) {
        Integer index = indexes.get(column);
        if (index == null) {
            throw new ConfigException("Config header error: file=" + file
                    + ", config=" + config + ", field=" + field
                    + ", column=" + column + ", reason=missing column");
        }
        return index;
    }

    public static ConfigHeader read(ConfigCsvReader reader, String file, String config,
                                    int nameLine) {
        if (nameLine < 1) {
            throw new ConfigException("Config header error: file=" + file
                    + ", config=" + config + ", reason=invalid name line: " + nameLine);
        }
        try {
            int line = 0;
            while (reader.readLine()) {
                line++;
                if (line < nameLine) {
                    continue;
                }
                if (reader.isComment()) {
                    throw new ConfigException("Config header error: file=" + file
                            + ", config=" + config + ", line=" + nameLine
                            + ", reason=name line is comment");
                }
                Map<String, Integer> indexes = new HashMap<>();
                int index = 0;
                while (reader.readField()) {
                    String column = reader.field().toString();
                    if (indexes.putIfAbsent(column, index) != null) {
                        throw new ConfigException("Config header error: file=" + file
                                + ", config=" + config + ", column=" + column
                                + ", reason=duplicate column");
                    }
                    index++;
                }
                return new ConfigHeader(indexes);
            }
        } catch (Exception e) {
            if (e instanceof ConfigException configException) {
                throw configException;
            }
            throw new ConfigException("Config header error: file=" + file
                    + ", config=" + config + ", reason=unable to read header", e);
        }
        throw new ConfigException("Config header error: file=" + file
                + ", config=" + config + ", reason=missing header");
    }
}
