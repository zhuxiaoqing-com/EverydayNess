package org.evd.game.gencode.db;

import java.util.List;

final class DbDirtyTableRegistryRenderer {
    String render(String packageName, List<DbDirtyEntityMeta> entities) {
        StringBuilder sb = new StringBuilder(1024 + entities.size() * 96);
        sb.append("package ").append(packageName).append(";\n\n");
        sb.append("import org.evd.game.runtime.Db.table.Mdb;\n");
        sb.append("import org.evd.game.runtime.Db.table.TableRegistry;\n");
        sb.append("\n");
        sb.append("public final class DbTableRegistry implements TableRegistry {\n");
        sb.append("    @Override\n");
        sb.append("    public void register(Mdb mdb) {\n");
        for (DbDirtyEntityMeta entity : entities) {
            sb.append("        mdb.registerTable(")
                    .append(entity.tableTypeName()).append(".class, ")
                    .append(entity.internalTableTypeName()).append(".class);\n");
        }
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }
}
