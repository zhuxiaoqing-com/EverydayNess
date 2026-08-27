package org.evd.game.common.config.table;

import lombok.Getter;
import org.evd.game.annotation.config.Column;
import org.evd.game.annotation.config.Config;
import org.evd.game.annotation.config.Converter;
import org.evd.game.common.bean.ItemRef;
import org.evd.game.common.config.converter.TrimmedStringConverter;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 覆盖配置解析器各类型和空值行为的示例配置表。 */
@Config(file = "config_parser_test.csv", keys = {"id"})
@Getter
public class ConfigParserTestConfig {
    private int id;
    private byte byteValue;
    private short shortValue;
    private int intValue;
    private long longValue;
    private float floatValue;
    private double doubleValue;
    private boolean booleanValue;
    private char charDefault;
    private Integer boxedInt;
    private Boolean boxedBoolean;
    private String plainText;
    @Column("csv_name")
    private String aliasedName;
    private Grade grade;
    private ItemRef item;
    private List<Integer> intList;
    private Set<String> textSet;
    private Map<Integer, Integer> intMap;
    private int[] intArray;
    private List<ItemRef> itemList;
    @Converter(TrimmedStringConverter.class)
    private String convertedText;

    public enum Grade {
        BASIC,
        ADVANCED
    }
}
