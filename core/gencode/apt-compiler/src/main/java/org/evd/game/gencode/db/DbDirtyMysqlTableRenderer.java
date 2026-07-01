package org.evd.game.gencode.db;

import java.util.List;

final class DbDirtyMysqlTableRenderer {
    String render(DbDirtyEntityMeta entity) {
        String keyType = entity.primaryKeyField.type.boxedType();
        List<DbDirtyFieldMeta> tableFields = entity.tableFields();
        var importedEntityTypes = DbDirtyTypeNameSupport.collectImportedEntityTypes(entity);
        int primaryKeyIndex = 0;
        for (int i = 0; i < tableFields.size(); i++) {
            if (tableFields.get(i).primaryKey) {
                primaryKeyIndex = i;
                break;
            }
        }
        StringBuilder sb = new StringBuilder(16384);
        sb.append("package ").append(entity.internalTablePackage).append(";\n\n");
        sb.append("import ").append(entity.beanPackage).append(".*;\n");
        DbDirtyTypeNameSupport.appendImports(sb, importedEntityTypes);
        sb.append("import org.evd.game.base.DirtyObject;\n");
        if (entity.usesList()) {
            sb.append("import org.evd.game.runtime.Db.collection.XArrayList;\n");
        }
        if (entity.usesMap()) {
            sb.append("import org.evd.game.runtime.Db.collection.XHashMap;\n");
        }
        if (entity.usesSet()) {
            sb.append("import org.evd.game.runtime.Db.collection.XHashSet;\n");
        }
        sb.append("import org.evd.game.runtime.Db.serialize.DBReq;\n");
        sb.append("import org.evd.game.runtime.Db.serialize.DBRsp;\n");
        sb.append("import org.evd.game.runtime.Db.serialize.DbOpType;\n");
        sb.append("import org.evd.game.runtime.Db.serialize.DbTableField;\n");
        sb.append("import org.evd.game.runtime.Db.serialize.DbValue;\n");
        sb.append("import org.evd.game.runtime.Db.serialize.MysqlReq;\n");
        sb.append("import org.evd.game.runtime.Db.serialize.MysqlRsp;\n");
        sb.append("import org.evd.game.runtime.Db.serialize.MysqlTableMeta;\n");
        sb.append("import org.evd.game.runtime.Db.table.TTable;\n");
        sb.append("import com.alibaba.fastjson2.JSON;\n");
        sb.append("import com.alibaba.fastjson2.TypeReference;\n");
        sb.append("import java.util.ArrayList;\n");
        sb.append("import java.util.LinkedHashMap;\n");
        sb.append("import java.util.List;\n");
        sb.append("import java.util.Map;\n");
        sb.append("import java.util.Objects;\n\n");

        sb.append("public final class ").append(entity.internalTableClassName)
                .append(" extends TTable<").append(keyType).append(", ").append(entity.beanClassName).append("> {\n");
        sb.append("    private static final String TABLE_NAME = \"").append(entity.tableName()).append("\";\n");
        sb.append("    private static final String KEY_COLUMN_NAME = \"")
                .append(entity.primaryKeyField.columnName).append("\";\n");
        sb.append("    private static final List<String> COLUMN_NAMES = List.of(");
        for (int i = 0; i < tableFields.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("\"").append(tableFields.get(i).columnName).append("\"");
        }
        sb.append(");\n");
        sb.append("    private static final String CREATE_TABLE_SQL = \"\"\"\n");
        sb.append("            CREATE TABLE IF NOT EXISTS ").append(entity.tableName()).append(" (\n");
        for (int i = 0; i < tableFields.size(); i++) {
            DbDirtyFieldMeta field = tableFields.get(i);
            sb.append("                ").append(field.columnName).append(" ")
                    .append(DbDirtyRenderSupport.renderSqlType(field.type));
            if (field.primaryKey) {
                sb.append(" NOT NULL PRIMARY KEY");
            } else {
                sb.append(" NOT NULL");
            }
            if (i < tableFields.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("            ) ENGINE=INNODB DEFAULT CHARSET=UTF8MB4 COLLATE=UTF8MB4_GENERAL_CI\n");
        sb.append("            \"\"\";\n");
        sb.append("\n");

        sb.append("    private ").append(entity.internalTableClassName).append("() {\n");
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public String getName() {\n");
        sb.append("        return TABLE_NAME;\n");
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    protected ").append(entity.beanClassName).append(" newValue() {\n");
        sb.append("        return new ").append(entity.beanClassName).append("();\n");
        sb.append("    }\n\n");

        sb.append("    @Override\n");
        sb.append("    public DBReq createCreateTableDBReq() {\n");
        sb.append("        return createInitReq(CREATE_TABLE_SQL, new ArrayList<>());\n");
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public DBReq createGetDBReq(").append(keyType).append(" key) {\n");
        sb.append("        return createReq(DbOpType.GET, List.of(createKeyField(key)));\n");
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public DBReq createSaveDBReq(").append(entity.beanClassName).append(" value) {\n");
        sb.append("        return createReq(DbOpType.SAVE, List.of(toTableField(value)));\n");
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public DBReq createRemoveDBReq(").append(keyType).append(" key) {\n");
        sb.append("        return createReq(DbOpType.REMOVE, List.of(createKeyField(key)));\n");
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public DBReq createBatchGetDBReq(Map<").append(keyType).append(", ").append(entity.beanClassName).append("> map) {\n");
        sb.append("        return createReq(DbOpType.BATCH_GET, toKeyFieldList(map));\n");
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public DBReq createBatchSaveDBReq(Map<").append(keyType).append(", ").append(entity.beanClassName).append("> map) {\n");
        sb.append("        requireBatchMap(map);\n");
        sb.append("        List<DbTableField> tableFieldList = new ArrayList<>(map.size());\n");
        sb.append("        for (").append(entity.beanClassName).append(" value : map.values()) {\n");
        sb.append("            tableFieldList.add(toTableField(value));\n");
        sb.append("        }\n");
        sb.append("        return createReq(DbOpType.BATCH_SAVE, tableFieldList);\n");
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public DBReq createBatchRemoveDBReq(Map<").append(keyType).append(", ").append(entity.beanClassName).append("> map) {\n");
        sb.append("        return createReq(DbOpType.BATCH_REMOVE, toKeyFieldList(map));\n");
        sb.append("    }\n\n");

        sb.append("    @Override\n");
        sb.append("    public ").append(entity.beanClassName).append(" parseGetDBRsp(DBRsp rsp) {\n");
        sb.append("        MysqlRsp mysqlRsp = requireMysqlRsp(rsp);\n");
        sb.append("        List<DbTableField> tableFieldList = mysqlRsp.getTablFieldList();\n");
        sb.append("        if (tableFieldList == null || tableFieldList.isEmpty()) {\n");
        sb.append("            return null;\n");
        sb.append("        }\n");
        sb.append("        return parseRow(tableFieldList.get(0));\n");
        sb.append("    }\n\n");
        sb.append("    @Override\n");
        sb.append("    public Map<").append(keyType).append(", ").append(entity.beanClassName).append("> parseBatchGetDBRsp(DBRsp rsp) {\n");
        sb.append("        MysqlRsp mysqlRsp = requireMysqlRsp(rsp);\n");
        sb.append("        Map<").append(keyType).append(", ").append(entity.beanClassName).append("> result = new LinkedHashMap<>();\n");
        sb.append("        List<DbTableField> tableFieldList = mysqlRsp.getTablFieldList();\n");
        sb.append("        if (tableFieldList == null || tableFieldList.isEmpty()) {\n");
        sb.append("            return result;\n");
        sb.append("        }\n");
        sb.append("        for (DbTableField tableField : tableFieldList) {\n");
        sb.append("            ").append(keyType).append(" key = parseRowKey(tableField);\n");
        sb.append("            ").append(entity.beanClassName).append(" value = parseRow(tableField);\n");
        sb.append("            if (key == null) {\n");
        sb.append("                if (value != null) {\n");
        sb.append("                    result.put(getPrimaryKey(value), value);\n");
        sb.append("                }\n");
        sb.append("                continue;\n");
        sb.append("            }\n");
        sb.append("            result.put(key, value);\n");
        sb.append("        }\n");
        sb.append("        return result;\n");
        sb.append("    }\n\n");

        sb.append("    private DBReq createInitReq(String sql, List<DbTableField> tableFieldList) {\n");
        sb.append("        MysqlReq mysqlReq = new MysqlReq();\n");
        sb.append("        mysqlReq.setTableName(TABLE_NAME);\n");
        sb.append("        MysqlTableMeta tableMeta = new MysqlTableMeta();\n");
        sb.append("        tableMeta.setKeyColumnName(KEY_COLUMN_NAME);\n");
        sb.append("        tableMeta.setColumnNames(COLUMN_NAMES);\n");
        sb.append("        mysqlReq.setTableMeta(tableMeta);\n");
        sb.append("        mysqlReq.setSql(sql);\n");
        sb.append("        mysqlReq.setTablFieldList(tableFieldList);\n\n");
        sb.append("        DBReq dbReq = new DBReq();\n");
        sb.append("        dbReq.setDbOpType(DbOpType.CREATE_TABLE);\n");
        sb.append("        dbReq.setMysqlReq(mysqlReq);\n");
        sb.append("        return dbReq;\n");
        sb.append("    }\n\n");
        sb.append("    private DBReq createReq(DbOpType opType, List<DbTableField> tableFieldList) {\n");
        sb.append("        MysqlReq mysqlReq = new MysqlReq();\n");
        sb.append("        mysqlReq.setTableName(TABLE_NAME);\n");
        sb.append("        mysqlReq.setTablFieldList(tableFieldList);\n\n");
        sb.append("        DBReq dbReq = new DBReq();\n");
        sb.append("        dbReq.setDbOpType(opType);\n");
        sb.append("        dbReq.setMysqlReq(mysqlReq);\n");
        sb.append("        return dbReq;\n");
        sb.append("    }\n\n");
        sb.append("    private DbTableField createKeyField(").append(keyType).append(" key) {\n");
        sb.append("        Objects.requireNonNull(key, \"key 不能为空\");\n");
        sb.append("        return new DbTableField(List.of(new DbValue(key)));\n");
        sb.append("    }\n\n");
        sb.append("    private List<DbTableField> toKeyFieldList(Map<").append(keyType).append(", ").append(entity.beanClassName).append("> map) {\n");
        sb.append("        requireBatchMap(map);\n");
        sb.append("        List<DbTableField> tableFieldList = new ArrayList<>(map.size());\n");
        sb.append("        for (").append(keyType).append(" key : map.keySet()) {\n");
        sb.append("            tableFieldList.add(createKeyField(key));\n");
        sb.append("        }\n");
        sb.append("        return tableFieldList;\n");
        sb.append("    }\n\n");
        sb.append("    private ").append(keyType).append(" getPrimaryKey(").append(entity.beanClassName).append(" value) {\n");
        sb.append("        return value.get").append(entity.primaryKeyField.methodSuffix).append("();\n");
        sb.append("    }\n\n");
        sb.append("    private DbTableField toTableField(").append(entity.beanClassName).append(" value) {\n");
        sb.append("        Objects.requireNonNull(value, \"value 不能为空\");\n");
        sb.append("        List<DbValue> valueList = new ArrayList<>(").append(tableFields.size()).append(");\n");
        for (DbDirtyFieldMeta field : tableFields) {
            if (field.type.isMysqlScalar()) {
                sb.append("        valueList.add(new DbValue(value.get").append(field.methodSuffix).append("()));\n");
            } else {
                sb.append("        valueList.add(new DbValue(serialize").append(field.methodSuffix)
                        .append("(value.get").append(field.methodSuffix).append("())));\n");
            }
        }
        sb.append("        return new DbTableField(valueList);\n");
        sb.append("    }\n\n");
        sb.append("    private ").append(keyType).append(" parseRowKey(DbTableField tableField) {\n");
        sb.append("        List<DbValue> valueList = tableField.getValueList();\n");
        sb.append("        if (valueList == null || valueList.size() <= ").append(primaryKeyIndex).append(") {\n");
        sb.append("            return null;\n");
        sb.append("        }\n");
        sb.append("        return ")
                .append(DbDirtyRenderSupport.renderDbValueCast(entity.primaryKeyField.type, "valueList.get(" + primaryKeyIndex + ").getV()")).append(";\n");
        sb.append("    }\n\n");
        sb.append("    private ").append(entity.beanClassName).append(" parseRow(DbTableField tableField) {\n");
        sb.append("        List<DbValue> valueList = tableField.getValueList();\n");
        sb.append("        if (valueList == null || valueList.size() < ").append(tableFields.size()).append(") {\n");
        sb.append("            return null;\n");
        sb.append("        }\n");
        sb.append("        ").append(entity.beanClassName).append(" value = new ").append(entity.beanClassName).append("();\n");
        for (int i = 0; i < tableFields.size(); i++) {
            DbDirtyFieldMeta field = tableFields.get(i);
            if (field.type.isMysqlScalar()) {
                sb.append("        value.set").append(field.methodSuffix).append("(")
                        .append(DbDirtyRenderSupport.renderDbValueCast(field.type, "valueList.get(" + i + ").getV()"))
                        .append(");\n");
            } else {
                sb.append("        value.set").append(field.methodSuffix).append("(deserialize")
                        .append(field.methodSuffix).append("((String) valueList.get(").append(i)
                        .append(").getV(), value));\n");
            }
        }
        sb.append("        value.dirty = false;\n");
        sb.append("        return value;\n");
        sb.append("    }\n\n");
        for (DbDirtyFieldMeta field : tableFields) {
            if (field.type.isMysqlScalar()) {
                continue;
            }
            sb.append("    private String serialize").append(field.methodSuffix).append("(")
                    .append(field.type.getterType).append(" value) {\n");
            sb.append("        return JSON.toJSONString(value);\n");
            sb.append("    }\n\n");
            sb.append("    private ").append(field.type.fieldType).append(" deserialize")
                    .append(field.methodSuffix).append("(String text, DirtyObject owner) {\n");
            sb.append("        if (text == null || text.isEmpty() || \"null\".equals(text)) {\n");
            sb.append("            return ").append(DbDirtyRenderSupport.renderDefaultValue(field.type, "owner")).append(";\n");
            sb.append("        }\n");
            appendMysqlJsonDeserializeBody(sb, field.type, "text", "owner", "        ");
            sb.append("    }\n\n");
        }
        sb.append("    private MysqlRsp requireMysqlRsp(DBRsp rsp) {\n");
        sb.append("        Objects.requireNonNull(rsp, \"rsp 不能为空\");\n");
        sb.append("        if (!rsp.isSuccess()) {\n");
        sb.append("            throw new IllegalArgumentException(\"db rsp fail: \" + rsp.getExceptionMessage());\n");
        sb.append("        }\n");
        sb.append("        MysqlRsp mysqlRsp = rsp.getMysqlRsp();\n");
        sb.append("        if (mysqlRsp == null) {\n");
        sb.append("            throw new IllegalArgumentException(\"db rsp mysqlRsp 不能为空\");\n");
        sb.append("        }\n");
        sb.append("        return mysqlRsp;\n");
        sb.append("    }\n\n");
        sb.append("    private void requireBatchMap(Map<").append(keyType).append(", ").append(entity.beanClassName).append("> map) {\n");
        sb.append("        if (map == null || map.isEmpty()) {\n");
        sb.append("            throw new IllegalArgumentException(\"batch map 不能为空\");\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
        sb.append("}\n");
        return DbDirtyTypeNameSupport.rewriteImportedTypeNames(sb.toString(), importedEntityTypes);
    }

    private void appendMysqlJsonDeserializeBody(StringBuilder sb, DbDirtyTypeMeta type, String textExpr, String ownerExpr, String indent) {
        switch (type.kind) {
            case LIST -> {
                sb.append(indent).append("java.util.List<").append(type.elementType.fieldType).append("> data = JSON.parseObject(")
                        .append(textExpr).append(", new TypeReference<java.util.List<").append(type.elementType.fieldType)
                        .append(">>() {\n");
                sb.append(indent).append("});\n");
                sb.append(indent).append("XArrayList<").append(type.elementType.fieldType).append("> result = new XArrayList<>(")
                        .append(ownerExpr).append(");\n");
                sb.append(indent).append("if (data != null) {\n");
                if (type.elementType.kind == DbDirtyTypeKind.ENTITY) {
                    sb.append(indent).append("    for (").append(type.elementType.fieldType).append(" item : data) {\n");
                    sb.append(indent).append("        if (item != null) {\n");
                    sb.append(indent).append("            item.setParent(result);\n");
                    sb.append(indent).append("        }\n");
                    sb.append(indent).append("        result.add(item);\n");
                    sb.append(indent).append("    }\n");
                } else {
                    sb.append(indent).append("    result.addAll(data);\n");
                }
                sb.append(indent).append("}\n");
                sb.append(indent).append("return result;\n");
            }
            case SET -> {
                sb.append(indent).append("java.util.Set<").append(type.elementType.fieldType).append("> data = JSON.parseObject(")
                        .append(textExpr).append(", new TypeReference<java.util.Set<").append(type.elementType.fieldType)
                        .append(">>() {\n");
                sb.append(indent).append("});\n");
                sb.append(indent).append("XHashSet<").append(type.elementType.fieldType).append("> result = new XHashSet<>(")
                        .append(ownerExpr).append(");\n");
                sb.append(indent).append("if (data != null) {\n");
                if (type.elementType.kind == DbDirtyTypeKind.ENTITY) {
                    sb.append(indent).append("    for (").append(type.elementType.fieldType).append(" item : data) {\n");
                    sb.append(indent).append("        if (item != null) {\n");
                    sb.append(indent).append("            item.setParent(result);\n");
                    sb.append(indent).append("        }\n");
                    sb.append(indent).append("        result.add(item);\n");
                    sb.append(indent).append("    }\n");
                } else {
                    sb.append(indent).append("    result.addAll(data);\n");
                }
                sb.append(indent).append("}\n");
                sb.append(indent).append("return result;\n");
            }
            case MAP -> {
                sb.append(indent).append("java.util.Map<").append(type.keyType.fieldType).append(", ").append(type.valueType.fieldType)
                        .append("> data = JSON.parseObject(").append(textExpr).append(", new TypeReference<java.util.Map<")
                        .append(type.keyType.fieldType).append(", ").append(type.valueType.fieldType).append(">>() {\n");
                sb.append(indent).append("});\n");
                sb.append(indent).append("XHashMap<").append(type.keyType.fieldType).append(", ").append(type.valueType.fieldType)
                        .append("> result = new XHashMap<>(").append(ownerExpr).append(");\n");
                sb.append(indent).append("if (data != null) {\n");
                sb.append(indent).append("    data.forEach((key, value) -> {\n");
                if (type.keyType.kind == DbDirtyTypeKind.ENTITY) {
                    sb.append(indent).append("        if (key != null) {\n");
                    sb.append(indent).append("            key.setParent(result);\n");
                    sb.append(indent).append("        }\n");
                }
                if (type.valueType.kind == DbDirtyTypeKind.ENTITY) {
                    sb.append(indent).append("        if (value != null) {\n");
                    sb.append(indent).append("            value.setParent(result);\n");
                    sb.append(indent).append("        }\n");
                }
                sb.append(indent).append("        result.put(key, value);\n");
                sb.append(indent).append("    });\n");
                sb.append(indent).append("}\n");
                sb.append(indent).append("return result;\n");
            }
            case ENTITY -> {
                sb.append(indent).append(type.fieldType).append(" result = JSON.parseObject(").append(textExpr)
                        .append(", ").append(type.fieldType).append(".class);\n");
                sb.append(indent).append("if (result != null) {\n");
                sb.append(indent).append("    result.setParent(").append(ownerExpr).append(");\n");
                sb.append(indent).append("}\n");
                sb.append(indent).append("return result;\n");
            }
            default -> {
                sb.append(indent).append("return JSON.parseObject(").append(textExpr).append(", new TypeReference<")
                        .append(type.fieldType).append(">() {\n");
                sb.append(indent).append("});\n");
            }
        }
    }
}
