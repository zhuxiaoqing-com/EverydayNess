package org.evd.game.runtime.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 供 APT 生成代码调用的基础类型和容器解析工具。 */
public final class ConfigValueParser {
    private static final char ELEMENT_SEPARATOR = '#';
    private static final char MAP_KEY_VALUE_SEPARATOR = '&';

    private ConfigValueParser() {
    }

    @FunctionalInterface
    public interface Parser<T> {
        T parse(CharSequence value);
    }

    public static byte parseByte(CharSequence value) {
        return checkedByte(parseLong(value));
    }

    public static short parseShort(CharSequence value) {
        long parsed = parseLong(value);
        if (parsed < Short.MIN_VALUE || parsed > Short.MAX_VALUE) {
            throw new NumberFormatException("short overflow: " + value);
        }
        return (short) parsed;
    }

    public static int parseInt(CharSequence value) {
        long parsed = parseLong(value);
        if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
            throw new NumberFormatException("int overflow: " + value);
        }
        return (int) parsed;
    }

    public static long parseLong(CharSequence value) {
        if (value.isEmpty()) {
            throw new NumberFormatException("empty number");
        }
        int index = 0;
        boolean negative = value.charAt(0) == '-';
        if (negative || value.charAt(0) == '+') {
            index++;
        }
        if (index == value.length()) {
            throw new NumberFormatException("sign without digits: " + value);
        }
        long result = 0;
        while (index < value.length()) {
            char digit = value.charAt(index++);
            if (digit < '0' || digit > '9') {
                throw new NumberFormatException("invalid number: " + value);
            }
            int numeric = digit - '0';
            if (result < (Long.MIN_VALUE + numeric) / 10) {
                throw new NumberFormatException("long overflow: " + value);
            }
            result = result * 10 - numeric;
        }
        if (!negative) {
            if (result == Long.MIN_VALUE) {
                throw new NumberFormatException("long overflow: " + value);
            }
            return -result;
        }
        return result;
    }

    public static float parseFloat(CharSequence value) {
        return Float.parseFloat(value.toString());
    }

    public static double parseDouble(CharSequence value) {
        return Double.parseDouble(value.toString());
    }

    public static boolean parseBoolean(CharSequence value) {
        if (equalsIgnoreCase(value, "true")) {
            return true;
        }
        if (equalsIgnoreCase(value, "false")) {
            return false;
        }
        throw new IllegalArgumentException("expected true or false");
    }

    private static byte checkedByte(long value) {
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
            throw new NumberFormatException("byte overflow: " + value);
        }
        return (byte) value;
    }

    private static boolean equalsIgnoreCase(CharSequence value, String expected) {
        if (value.length() != expected.length()) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if (Character.toLowerCase(value.charAt(i)) != Character.toLowerCase(expected.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static <T> List<T> parseList(CharSequence value, Parser<T> parser) {
        return parseList(value, ELEMENT_SEPARATOR, parser);
    }

    public static <T> List<T> parseList(CharSequence value, char separator, Parser<T> parser) {
        List<T> result = new ArrayList<>();
        if (value.length() == 0) {
            return result;
        }
        parseElements(value, separator, parser, result);
        return result;
    }

    public static <T> Set<T> parseSet(CharSequence value, Parser<T> parser) {
        return parseSet(value, ELEMENT_SEPARATOR, parser);
    }

    public static <T> Set<T> parseSet(CharSequence value, char separator, Parser<T> parser) {
        Set<T> result = new LinkedHashSet<>();
        if (value.length() == 0) {
            return result;
        }
        parseElements(value, separator, parser, result);
        return result;
    }

    private static <T> void parseElements(CharSequence value, char separator, Parser<T> parser,
                                           Collection<T> result) {
        forEach(value, separator, token -> result.add(parser.parse(token)));
    }

    public static <K, V> Map<K, V> parseMap(CharSequence value, Parser<K> keyParser,
                                             Parser<V> valueParser) {
        return parseMap(value, ELEMENT_SEPARATOR, MAP_KEY_VALUE_SEPARATOR, keyParser, valueParser);
    }

    public static <K, V> Map<K, V> parseMap(CharSequence value,
                                             char entrySeparator,
                                             char keyValueSeparator,
                                             Parser<K> keyParser,
                                             Parser<V> valueParser) {
        Map<K, V> result = new LinkedHashMap<>();
        if (value.length() == 0) {
            return result;
        }
        forEach(value, entrySeparator, entry -> {
            int separator = indexOf(entry, keyValueSeparator);
            if (separator < 0) {
                throw new IllegalArgumentException("map entry must contain '&': " + entry);
            }
            K key = keyParser.parse(entry.subSequence(0, separator));
            V parsedValue = valueParser.parse(entry.subSequence(separator + 1, entry.length()));
            if (result.containsKey(key)) {
                throw new IllegalArgumentException("duplicate map key: " + key);
            }
            result.put(key, parsedValue);
        });
        return result;
    }

    private interface TokenConsumer {
        void accept(CharSequence token);
    }

    private static void forEach(CharSequence text, char separator, TokenConsumer consumer) {
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == separator) {
                consumer.accept(text.subSequence(start, i));
                start = i + 1;
            }
        }
    }

    private static int indexOf(CharSequence text, char value) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == value) {
                return i;
            }
        }
        return -1;
    }
}
