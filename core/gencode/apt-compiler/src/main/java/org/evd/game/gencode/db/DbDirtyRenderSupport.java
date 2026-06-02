package org.evd.game.gencode.db;

final class DbDirtyRenderSupport {
    private DbDirtyRenderSupport() {
    }

    static String renderPrimitiveWrite(String primitiveType, String outExpr, String valueExpr) {
        return switch (primitiveType) {
            case "boolean" -> outExpr + ".writeBoolean(" + valueExpr + ");";
            case "byte" -> outExpr + ".writeByte(" + valueExpr + ");";
            case "short" -> outExpr + ".writeShort(" + valueExpr + ");";
            case "int" -> outExpr + ".writeInt(" + valueExpr + ");";
            case "long" -> outExpr + ".writeLong(" + valueExpr + ");";
            case "float" -> outExpr + ".writeFloat(" + valueExpr + ");";
            case "double" -> outExpr + ".writeDouble(" + valueExpr + ");";
            case "char" -> outExpr + ".writeInt(" + valueExpr + ");";
            default -> outExpr + ".write(" + valueExpr + ");";
        };
    }

    static String renderPrimitiveRead(String primitiveType, String inExpr) {
        return switch (primitiveType) {
            case "boolean" -> inExpr + ".readBoolean()";
            case "byte" -> inExpr + ".readByte()";
            case "short" -> inExpr + ".readShort()";
            case "int" -> inExpr + ".readInt()";
            case "long" -> inExpr + ".readLong()";
            case "float" -> inExpr + ".readFloat()";
            case "double" -> inExpr + ".readDouble()";
            case "char" -> "(char) " + inExpr + ".readInt()";
            default -> "(" + primitiveType + ") " + inExpr + ".read()";
        };
    }

    static String renderObjectRead(String typeName, String inExpr) {
        return "(" + typeName + ") " + inExpr + ".read()";
    }

    static String renderDefaultValue(DbDirtyTypeMeta type, String parentExpr) {
        return switch (type.kind) {
            case STRING -> "\"\"";
            case LIST -> "new XArrayList<>(" + parentExpr + ")";
            case SET -> "new XHashSet<>(" + parentExpr + ")";
            case MAP -> "new XHashMap<>(" + parentExpr + ")";
            case PRIMITIVE -> switch (type.fieldType) {
                case "boolean" -> "false";
                case "byte" -> "(byte)0";
                case "short" -> "(short)0";
                case "int" -> "0";
                case "long" -> "0L";
                case "float" -> "0F";
                case "double" -> "0D";
                case "char" -> "'\\0'";
                default -> "0";
            };
            default -> null;
        };
    }

    static String renderSqlType(DbDirtyTypeMeta type) {
        if (type.kind == DbDirtyTypeKind.STRING) {
            return "VARCHAR(128)";
        }
        if (type.kind == DbDirtyTypeKind.PRIMITIVE) {
            return switch (type.fieldType) {
                case "byte", "short", "int", "char" -> "INT";
                case "long" -> "BIGINT";
                case "float", "double" -> "DOUBLE";
                case "boolean" -> "TINYINT(1)";
                default -> "VARCHAR(128)";
            };
        }
        if (type.kind == DbDirtyTypeKind.OTHER) {
            return switch (type.fieldType) {
                case "Byte", "Short", "Integer", "Character" -> "INT";
                case "Long" -> "BIGINT";
                case "Float", "Double" -> "DOUBLE";
                case "Boolean" -> "TINYINT(1)";
                case "String" -> "VARCHAR(128)";
                default -> "MEDIUMTEXT";
            };
        }
        return "MEDIUMTEXT";
    }

    static String renderDbValueCast(DbDirtyTypeMeta type, String expr) {
        if (type.kind == DbDirtyTypeKind.STRING) {
            return "(String) " + expr;
        }
        if (type.kind == DbDirtyTypeKind.PRIMITIVE || type.kind == DbDirtyTypeKind.OTHER) {
            return switch (type.fieldType) {
                case "byte", "Byte" -> "((Number) " + expr + ").byteValue()";
                case "short", "Short" -> "((Number) " + expr + ").shortValue()";
                case "int", "Integer" -> "((Number) " + expr + ").intValue()";
                case "long", "Long" -> "((Number) " + expr + ").longValue()";
                case "float", "Float" -> "((Number) " + expr + ").floatValue()";
                case "double", "Double" -> "((Number) " + expr + ").doubleValue()";
                case "boolean", "Boolean" -> "((Boolean) " + expr + ")";
                case "char", "Character" -> "(char) ((Number) " + expr + ").intValue()";
                case "String" -> "(String) " + expr;
                default -> "(" + type.fieldType + ") " + expr;
            };
        }
        return "(" + type.fieldType + ") " + expr;
    }
}
