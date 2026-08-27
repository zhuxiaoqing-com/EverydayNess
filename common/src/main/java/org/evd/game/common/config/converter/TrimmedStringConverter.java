package org.evd.game.common.config.converter;

import org.evd.game.annotation.config.ConfigConverter;

/** 示例字段转换器：读取字符串时去除首尾空白。 */
public class TrimmedStringConverter implements ConfigConverter<String> {
    @Override
    public String convert(CharSequence value) {
        return value.toString().trim();
    }
}
