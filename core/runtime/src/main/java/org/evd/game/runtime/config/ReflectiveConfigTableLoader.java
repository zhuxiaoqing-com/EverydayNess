package org.evd.game.runtime.config;

import org.evd.game.annotation.config.Column;
import org.evd.game.annotation.config.Config;
import org.evd.game.annotation.config.ConfigConverter;
import org.evd.game.annotation.config.Converter;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 启动阶段复用的反射配置加载器，字段元数据只构建一次。 */
@SuppressWarnings({"unchecked", "rawtypes"})
final class ReflectiveConfigTableLoader {
    private static final Map<Class<?>, TableMeta> META_CACHE = new ConcurrentHashMap<>();

    private ReflectiveConfigTableLoader() {
    }

    static void load(Path directory, Class<?>[] configTypes) {
        load(directory, configTypes, true);
    }

    static void load(Path directory, Class<?>[] configTypes, boolean replaceAll) {
        int nameLine = 2; // 第 2 行是字段名。
        int skipLineCount = 3; // 第 1～3 行属于表头，数据从第 4 行开始。
        List<LoadedTable> loadedTables = new ArrayList<>(configTypes.length);
        for (Class<?> configType : configTypes) {
            loadedTables.add(loadTable(directory, configType, nameLine, skipLineCount));
        }
        Map<Class<?>, ConfigTable> tables = new HashMap<>();
        for (LoadedTable loadedTable : loadedTables) {
            tables.put(loadedTable.type(), loadedTable.values());
        }
        if (replaceAll) {
            ConfigManager.replaceAll(tables);
        } else {
            ConfigManager.replaceLoaded(tables);
        }
    }

    private static LoadedTable loadTable(Path directory, Class<?> configType,
                                         int nameLine, int skipLineCount) {
        TableMeta meta = META_CACHE.computeIfAbsent(configType, ReflectiveConfigTableLoader::buildMeta);
        String fileName = meta.config.file();
        Path path = directory.resolve(fileName);
        ConfigTable table = meta.newTable();
        try (java.io.Reader source = Files.newBufferedReader(path)) {
            try (ConfigCsvReader csv = ConfigCsvReader.open(source)) {
                ConfigHeader header = ConfigHeader.read(csv, fileName,
                        meta.type.getSimpleName(), nameLine);
                int[] fieldIndexes = new int[meta.fields.size()];
                for (int i = 0; i < meta.fields.size(); i++) {
                    FieldMeta field = meta.fields.get(i);
                    fieldIndexes[i] = header.require(field.column, fileName,
                            meta.type.getSimpleName(), field.name);
                }

                long line = nameLine;
                while (csv.readLine()) {
                    line++;
                    if (line <= skipLineCount) {
                        continue;
                    }
                    if (csv.isComment()) {
                        continue;
                    }
                    Object config = meta.newInstance();
                    boolean[] present = new boolean[meta.fields.size()];
                    int column = 0;
                    while (csv.readField()) {
                        CharSequence value = csv.field();
                        for (int i = 0; i < meta.fields.size(); i++) {
                            FieldMeta field = meta.fields.get(i);
                            if (column != fieldIndexes[i]) {
                                continue;
                            }
                            try {
                                field.field.set(config, field.parser.parse(value));
                            } catch (Exception e) {
                                throw ConfigParseException.forField(fileName, line, field.column,
                                        meta.type.getSimpleName() + "." + field.name,
                                        field.type.getTypeName(), value, e);
                            }
                            present[i] = true;
                            break;
                        }
                        column++;
                    }
                    for (int i = 0; i < meta.fields.size(); i++) {
                        if (!present[i]) {
                            FieldMeta field = meta.fields.get(i);
                            throw ConfigParseException.forField(fileName, line, field.column,
                                    meta.type.getSimpleName() + "." + field.name,
                                    field.type.getTypeName(), "",
                                    new IllegalArgumentException("missing field value"));
                        }
                    }
                    Object[] keyValues = new Object[meta.keyFields.length];
                    for (int i = 0; i < meta.keyFields.length; i++) {
                        try {
                            keyValues[i] = meta.keyFields[i].field.get(config);
                        } catch (IllegalAccessException e) {
                            throw new ConfigException("读取配置 Key 失败: config="
                                    + meta.type.getSimpleName(), e);
                        }
                    }
                    if (meta.keyMode == KeyMode.SINGLE_INT) {
                        table.putInt(((Number) keyValues[0]).intValue(), config,
                                meta.type, fileName, line);
                    } else {
                        table.put(ConfigManager.key(keyValues), config,
                                meta.type, fileName, line);
                    }
                }
            }
        } catch (ConfigException e) {
            throw e;
        } catch (IOException e) {
            throw new ConfigException("Config load error: file=" + path, e);
        }
        return new LoadedTable(configType, table);
    }

    private static TableMeta buildMeta(Class<?> type) {
        Config config = type.getAnnotation(Config.class);
        if (config == null) {
            throw new ConfigException("缺少 @Config: class=" + type.getName());
        }
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            List<FieldMeta> fields = new ArrayList<>();
            Map<String, FieldMeta> fieldsByName = new HashMap<>();
            for (Field field : type.getDeclaredFields()) {
                // static 字段不要
                // 编译器偷偷生成的字段也不要
                // 只处理正常的实例字段
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                field.setAccessible(true);
                String column = field.getName();
                Column columnAnnotation = field.getAnnotation(Column.class);
                if (columnAnnotation != null) {
                    column = columnAnnotation.value();
                }
                FieldMeta meta = new FieldMeta(field, field.getName(), column, field.getGenericType(),
                        createParser(field));
                fields.add(meta);
                fieldsByName.put(meta.name, meta);
            }

            FieldMeta[] keyFields = new FieldMeta[config.keys().length];
            for (int i = 0; i < config.keys().length; i++) {
                FieldMeta field = fieldsByName.get(config.keys()[i]);
                if (field == null) {
                    throw new ConfigException("配置 Key 字段不存在: config=" + type.getSimpleName()
                            + ", field=" + config.keys()[i]);
                }
                keyFields[i] = field;
            }
            KeyMode keyMode = keyFields.length == 1
                    && (keyFields[0].field.getType() == int.class
                    || keyFields[0].field.getType() == Integer.class)
                    ? KeyMode.SINGLE_INT
                    : KeyMode.OBJECT;
            return new TableMeta(type, config, constructor, fields, keyFields, keyMode);
        } catch (NoSuchMethodException e) {
            throw new ConfigException("配置类必须有无参构造方法: class=" + type.getName(), e);
        }
    }

    private static ValueParser createParser(Field field) {
        Converter converter = field.getAnnotation(Converter.class);
        if (converter == null) {
            return createParser(field.getGenericType());
        }
        ValueParser parser = converterParser(converter.value(), field.getType());
        ValueParser defaultParser = createParser(field.getGenericType());
        return value -> isBlank(value)
                ? defaultParser.parse(value)
                : parser.parse(value);
    }

    /**
     * 根据字段的运行时泛型类型构造一次性解析器。
     *
     * <p>解析器只在首次加载某张配置表时创建，后续读取数据行直接复用，
     * 不会每行重新判断字段类型。字段类型只分为两类：一层集合和基础类型。
     * 基础类型包括基本类型、String、enum 以及需要通过 Converter 转换的对象。</p>
     *
     * @param genericType 字段的完整泛型类型，例如 List&lt;ItemRef&gt; 或 Map&lt;Integer, Integer&gt;
     * @return 将 CSV 字段内容转换为目标 Java 类型的解析器
    */
    private static ValueParser createParser(Type genericType) {
        if (genericType instanceof ParameterizedType parameterized) {
            return createCollectionParser(parameterized);
        }
        if (genericType instanceof Class<?> type) {
            if (type.isArray()) {
                ValueParser parser = createArrayParser(type.getComponentType());
                return value -> isBlank(value)
                        ? Array.newInstance(type.getComponentType(), 0)
                        : parser.parse(value);
            }
            return createBasicParser(type);
        }
        throw new ConfigException("不支持的配置字段类型: " + genericType.getTypeName());
    }

    private static boolean isBlank(CharSequence value) {
        return value.toString().isBlank();
    }

    /** 数组沿用 List 的 # 元素分隔符，数组元素只能是基础类型。 */
    private static ValueParser createArrayParser(Class<?> componentClass) {
        ValueParser elementParser = createBasicParser(componentClass);
        return value -> {
            List<?> elements = ConfigValueParser.parseList(value, elementParser::parse);
            Object array = Array.newInstance(componentClass, elements.size());
            for (int i = 0; i < elements.size(); i++) {
                Array.set(array, i, elements.get(i));
            }
            return array;
        };
    }

    /** 根据 List、Set、Map 的完整泛型参数生成一层集合解析器。 */
    private static ValueParser createCollectionParser(ParameterizedType parameterized) {
        Class<?> raw = (Class<?>) parameterized.getRawType();
        Type[] arguments = parameterized.getActualTypeArguments();
        if (List.class.isAssignableFrom(raw) && arguments.length == 1) {
            ValueParser elementParser = createBasicParser(arguments[0]);
            return value -> isBlank(value)
                    ? new ArrayList<>()
                    : ConfigValueParser.parseList(value, elementParser::parse);
        }
        if (Set.class.isAssignableFrom(raw) && arguments.length == 1) {
            ValueParser elementParser = createBasicParser(arguments[0]);
            return value -> isBlank(value)
                    ? new LinkedHashSet<>()
                    : ConfigValueParser.parseSet(value, elementParser::parse);
        }
        if (Map.class.isAssignableFrom(raw) && arguments.length == 2) {
            ValueParser keyParser = createBasicParser(arguments[0]);
            ValueParser valueParser = createBasicParser(arguments[1]);
            return value -> isBlank(value)
                    ? new LinkedHashMap<>()
                    : ConfigValueParser.parseMap(value, keyParser::parse, valueParser::parse);
        }
        throw new ConfigException("不支持的配置泛型: " + parameterized.getTypeName());
    }

    /**
     * 集合元素只能是基础类型，不能再次进入集合解析。
     * 基础类型也包括 enum 和通过 ConfigConverter 转换的对象。
     */
    private static ValueParser createBasicParser(Type type) {
        if (!(type instanceof Class<?> clazz) || clazz.isArray()
                || List.class.isAssignableFrom(clazz)
                || Set.class.isAssignableFrom(clazz)
                || Map.class.isAssignableFrom(clazz)) {
            throw new ConfigException("集合元素必须是基础类型: " + type.getTypeName());
        }
        return createBasicParser(clazz);
    }

    /** 为一个非泛型 Class 选择基础类型、enum 或自定义 Converter 解析器。 */
    private static ValueParser createBasicParser(Class<?> type) {
        if (type == char.class || type == Character.class) {
            return value -> {
                if (isBlank(value)) {
                    return type == char.class ? '\0' : null;
                }
                throw new ConfigException("不支持的配置字段类型: " + type.getName());
            };
        }
        if (type == byte.class) {
            return value -> isBlank(value) ? (byte) 0 : ConfigValueParser.parseByte(value);
        }
        if (type == Byte.class) {
            return value -> isBlank(value) ? null : ConfigValueParser.parseByte(value);
        }
        if (type == short.class) {
            return value -> isBlank(value) ? (short) 0 : ConfigValueParser.parseShort(value);
        }
        if (type == Short.class) {
            return value -> isBlank(value) ? null : ConfigValueParser.parseShort(value);
        }
        if (type == int.class) {
            return value -> isBlank(value) ? 0 : ConfigValueParser.parseInt(value);
        }
        if (type == Integer.class) {
            return value -> isBlank(value) ? null : ConfigValueParser.parseInt(value);
        }
        if (type == long.class) {
            return value -> isBlank(value) ? 0L : ConfigValueParser.parseLong(value);
        }
        if (type == Long.class) {
            return value -> isBlank(value) ? null : ConfigValueParser.parseLong(value);
        }
        if (type == float.class) {
            return value -> isBlank(value) ? 0F : ConfigValueParser.parseFloat(value);
        }
        if (type == Float.class) {
            return value -> isBlank(value) ? null : ConfigValueParser.parseFloat(value);
        }
        if (type == double.class) {
            return value -> isBlank(value) ? 0D : ConfigValueParser.parseDouble(value);
        }
        if (type == Double.class) {
            return value -> isBlank(value) ? null : ConfigValueParser.parseDouble(value);
        }
        if (type == boolean.class) {
            return value -> isBlank(value) ? false : ConfigValueParser.parseBoolean(value);
        }
        if (type == Boolean.class) {
            return value -> isBlank(value) ? null : ConfigValueParser.parseBoolean(value);
        }
        if (type == String.class) {
            return value -> isBlank(value) ? "" : value.toString();
        }
        if (type.isEnum()) {
            return value -> isBlank(value) ? null
                    : Enum.valueOf(type.asSubclass(Enum.class), value.toString());
        }
        final ValueParser parser;
        if (ConfigConverter.class.isAssignableFrom(type)) {
            parser = converterParser(type, type);
        } else {
            Class<? extends ConfigConverter<?>> defaultConverter = ConfigManager.defaultConverter(type);
            if (defaultConverter == null) {
                throw new ConfigException("找不到配置类型转换器: type=" + type.getName());
            }
            parser = converterParser(defaultConverter, type);
        }
        return value -> isBlank(value) ? newInstance(type) : parser.parse(value);
    }

    private static ValueParser converterParser(Class<?> converterType, Class<?> targetType) {
        Object converter = newInstance(converterType);
        if (!(converter instanceof ConfigConverter<?> configConverter)) {
            throw new ConfigException("配置转换器必须实现 ConfigConverter: converter="
                    + converterType.getName() + ", target=" + targetType.getName());
        }
        return value -> ((ConfigConverter<Object>) configConverter).convert(value);
    }

    private static Object newInstance(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new ConfigException("配置转换器必须有无参构造方法: class=" + type.getName(), e);
        }
    }

    @FunctionalInterface
    private interface ValueParser {
        Object parse(CharSequence value);
    }

    private record LoadedTable(Class<?> type, ConfigTable values) {
    }

    private enum KeyMode {
        SINGLE_INT,
        OBJECT
    }

    private static final class TableMeta {
        private final Class<?> type;
        private final Config config;
        private final Constructor<?> constructor;
        private final List<FieldMeta> fields;
        private final FieldMeta[] keyFields;
        private final KeyMode keyMode;

        private TableMeta(Class<?> type, Config config, Constructor<?> constructor,
                          List<FieldMeta> fields, FieldMeta[] keyFields, KeyMode keyMode) {
            this.type = type;
            this.config = config;
            this.constructor = constructor;
            this.fields = fields;
            this.keyFields = keyFields;
            this.keyMode = keyMode;
        }

        private ConfigTable newTable() {
            return keyMode == KeyMode.SINGLE_INT ? new IntObjectTable() : new ObjectConfigTable();
        }

        private Object newInstance() {
            try {
                return constructor.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new ConfigException("创建配置对象失败: class=" + type.getName(), e);
            }
        }
    }

    private static final class FieldMeta {
        private final Field field;
        private final String name;
        private final String column;
        private final Type type;
        private final ValueParser parser;
        private FieldMeta(Field field, String name, String column, Type type, ValueParser parser) {
            this.field = field;
            this.name = name;
            this.column = column;
            this.type = type;
            this.parser = parser;
        }
    }
}
