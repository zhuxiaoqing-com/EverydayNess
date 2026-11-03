package org.evd.game.gencode.db;

import java.util.List;

final class DbDirtyTableRegistryRenderer {
    String render(String packageName, List<DbDirtyEntityMeta> entities) {
        StringBuilder sb = new StringBuilder(1024 + entities.size() * 96);
        sb.append("package ").append(packageName).append(";\n\n");
        sb.append("import org.evd.game.runtime.Db.table.DbTableRegistrar;\n");
        sb.append("import org.evd.game.runtime.Db.table.TableRegistry;\n");
        sb.append("\n");
        sb.append("public final class DbTableRegistry implements TableRegistry {\n");
        sb.append("    @Override\n");
        sb.append("    public void register(DbTableRegistrar registrar) {\n");
        for (DbDirtyEntityMeta entity : entities) {
            sb.append("        registrar.registerTable(")
                    .append(entity.tableTypeName()).append(".class, ")
                    .append(entity.beanPackage).append(".").append(entity.beanClassName).append(".class, ")
                    .append(entity.internalTableTypeName()).append(".class, ")
                    .append("\"").append(entity.tableName()).append("\", ")
                    .append(keySampleExpression(entity)).append(");\n");
        }
        sb.append("    }\n");
        sb.append("\n");
        sb.append("    @Override\n");
        sb.append("    public int tableCount() {\n");
        sb.append("        return ").append(entities.size()).append(";\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String keySampleExpression(DbDirtyEntityMeta entity) {
        if (entity.primaryKeyField == null) {
            throw new IllegalStateException("DB 表缺少主键样本: " + entity.beanClassName);
        }
        return switch (entity.primaryKeyField.type.fieldType) {
            case "int" -> "1";
            case "long" -> "1L";
            case "String" -> "\"sample\"";
            default -> throw new IllegalStateException("APT DB 主键类型不受快照 Key 支持: "
                    + entity.primaryKeyField.type.fieldType + ", bean=" + entity.beanClassName);
        };
    }
}
