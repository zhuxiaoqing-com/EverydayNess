package org.evd.game.common.config;

import org.evd.game.common.bean.ItemRef;
import org.evd.game.common.config.table.ConfigParserTestConfig;
import org.evd.game.common.config.table.ConfigParserTestConfigs;
import org.evd.game.runtime.config.ConfigException;
import org.evd.game.runtime.config.ConfigManager;
import org.evd.game.runtime.config.ConfigParseException;
import org.evd.game.runtime.config.ConfigTableInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTableTest {
    private static final String FILE_NAME = "config_parser_test.csv";

    @TempDir
    Path tempDir;

    @BeforeEach
    @AfterEach
    void clearConfig() {
        ConfigManager.clear();
    }

    @Test
    void load_shouldParseValuesDefaultsCollectionsObjectsAndQuotedText() {
        loadTestTable(productionCsvDir());

        ConfigParserTestConfig values = ConfigParserTestConfigs.get(1);
        assertEquals(7, values.getByteValue());
        assertEquals(-8, values.getShortValue());
        assertEquals(123, values.getIntValue());
        assertEquals(4_567_890_123L, values.getLongValue());
        assertEquals(1.25F, values.getFloatValue());
        assertEquals(2.5D, values.getDoubleValue());
        assertTrue(values.isBooleanValue());
        assertEquals('\0', values.getCharDefault());
        assertEquals(42, values.getBoxedInt());
        assertEquals(Boolean.TRUE, values.getBoxedBoolean());
        assertEquals("hello", values.getPlainText());
        assertEquals("aliased", values.getAliasedName());
        assertEquals(ConfigParserTestConfig.Grade.BASIC, values.getGrade());
        assertItem(values.getItem(), 100, 2);
        assertEquals(List.of(1, 2, 3), values.getIntList());
        assertEquals(Set.of("a", "b"), values.getTextSet());
        assertEquals(Map.of(1, 10, 2, 20), values.getIntMap());
        assertArrayEquals(new int[]{4, 5}, values.getIntArray());
        assertEquals(2, values.getItemList().size());
        assertItem(values.getItemList().get(0), 100, 1);
        assertItem(values.getItemList().get(1), 200, 2);
        assertEquals("converted", values.getConvertedText());

        ConfigParserTestConfig defaults = ConfigParserTestConfigs.get(2);
        assertEquals(0, defaults.getByteValue());
        assertEquals(0, defaults.getShortValue());
        assertEquals(0, defaults.getIntValue());
        assertEquals(0L, defaults.getLongValue());
        assertEquals(0F, defaults.getFloatValue());
        assertEquals(0D, defaults.getDoubleValue());
        assertFalse(defaults.isBooleanValue());
        assertEquals('\0', defaults.getCharDefault());
        assertNull(defaults.getBoxedInt());
        assertNull(defaults.getBoxedBoolean());
        assertEquals("", defaults.getPlainText());
        assertEquals("", defaults.getAliasedName());
        assertNull(defaults.getGrade());
        assertNotNull(defaults.getItem());
        assertEquals(0, defaults.getItem().getItemId());
        assertEquals(0, defaults.getItem().getCount());
        assertTrue(defaults.getIntList().isEmpty());
        assertTrue(defaults.getTextSet().isEmpty());
        assertTrue(defaults.getIntMap().isEmpty());
        assertEquals(0, defaults.getIntArray().length);
        assertTrue(defaults.getItemList().isEmpty());
        assertEquals("", defaults.getConvertedText());

        ConfigParserTestConfig quoted = ConfigParserTestConfigs.get(3);
        assertEquals("  plain text  ", quoted.getPlainText());
        assertEquals("  alias text  ", quoted.getAliasedName());
        assertEquals("explicit converter", quoted.getConvertedText());
        assertEquals(9, quoted.getByteValue());
        assertEquals(10, quoted.getShortValue());
        assertEquals(11, quoted.getIntValue());
        assertEquals(12L, quoted.getLongValue());
        assertEquals(List.of(6, 7), quoted.getIntList());
        assertEquals(Map.of(8, 9, 10, 11), quoted.getIntMap());
    }

    @Test
    void initAfterClear_shouldLoadAgain() {
        Path directory = productionCsvDir();

        ConfigTableInitializer.init(directory);
        assertNotNull(ConfigParserTestConfigs.get(1));

        ConfigManager.clear();
        assertNull(ConfigParserTestConfigs.get(1));

        ConfigTableInitializer.init(directory);
        assertNotNull(ConfigParserTestConfigs.get(1));
    }

    @Test
    void duplicateKey_shouldFailWithConfigContext() throws IOException {
        writeTable(tempDir, "# table", header(), "# data", row(1), row(1));
        registerItemRefConverter();

        ConfigException exception = assertThrows(ConfigException.class,
                () -> ConfigManager.load(tempDir, ConfigParserTestConfig.class));

        assertTrue(exception.getMessage().contains("duplicate key"));
        assertTrue(exception.getMessage().contains("config_parser_test.csv"));
    }

    @Test
    void missingColumn_shouldFailBeforeReadingData() throws IOException {
        String header = header().replace("intValue", "missingIntValue");
        writeTable(tempDir, "# table", header, "# data", row(1));
        registerItemRefConverter();

        ConfigException exception = assertThrows(ConfigException.class,
                () -> ConfigManager.load(tempDir, ConfigParserTestConfig.class));

        assertTrue(exception.getMessage().contains("missing column"));
        assertTrue(exception.getMessage().contains("intValue"));
    }

    @Test
    void invalidValue_shouldReportFileLineFieldAndValue() throws IOException {
        String invalidRow = row(1).replaceFirst(", 123 ,", ", invalid ,");
        writeTable(tempDir, "# table", header(), "# data", invalidRow);
        registerItemRefConverter();

        ConfigParseException exception = assertThrows(ConfigParseException.class,
                () -> ConfigManager.load(tempDir, ConfigParserTestConfig.class));

        assertTrue(exception.getMessage().contains("config_parser_test.csv"));
        assertTrue(exception.getMessage().contains("line=4"));
        assertTrue(exception.getMessage().contains("ConfigParserTestConfig.intValue"));
        assertTrue(exception.getMessage().contains("value=invalid"));
    }

    private void loadTestTable(Path directory) {
        registerItemRefConverter();
        ConfigManager.load(directory, ConfigParserTestConfig.class);
    }

    private void registerItemRefConverter() {
        ConfigManager.registerDefaultConverter(ItemRef.class, ItemRef.class);
    }

    private Path productionCsvDir() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path directory = current.resolve("csv");
            if (Files.isRegularFile(directory.resolve(FILE_NAME))) {
                return directory;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("找不到测试 CSV 目录: " + FILE_NAME);
    }

    private void writeTable(Path directory, String... rows) throws IOException {
        Files.writeString(directory.resolve(FILE_NAME), String.join(System.lineSeparator(), rows));
    }

    private String header() {
        return "id,byteValue,shortValue,intValue,longValue,floatValue,doubleValue,booleanValue,charDefault,"
                + "boxedInt,boxedBoolean,plainText,csv_name,grade,item,intList,textSet,intMap,intArray,itemList,convertedText";
    }

    private String row(int id) {
        return id + ", 7 , -8 , 123 , 4567890123 , 1.25 , 2.5 , TRUE, , 42, true, hello, aliased, BASIC, "
                + "100&2, 1#2#3, a#b#a, 1&10#2&20, 4#5, 100&1#200&2, converted";
    }

    private void assertItem(ItemRef item, int itemId, int count) {
        assertNotNull(item);
        assertEquals(itemId, item.getItemId());
        assertEquals(count, item.getCount());
    }
}
