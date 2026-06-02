package org.evd.game.gencode.db;

final class DbDirtyTableFacadeRenderer {
    String render(DbDirtyEntityMeta entity) {
        StringBuilder sb = new StringBuilder(2048);
        String keyType = entity.primaryKeyField.type.boxedType();
        sb.append("package ").append(entity.tablePackage).append(";\n\n");
        sb.append("import ").append(entity.beanPackage).append(".").append(entity.beanClassName).append(";\n");
        sb.append("import ").append(entity.internalTablePackage).append(".").append(entity.internalTableClassName).append(";\n");
        sb.append("import org.evd.game.runtime.Db.table.Mdb;\n");
        sb.append("import org.evd.game.runtime.Db.table.TTable;\n");
        sb.append("import org.evd.game.runtime.Service;\n\n");
        sb.append("public final class ").append(entity.tableClassName).append(" {\n");
        sb.append("    private ").append(entity.tableClassName).append("() {\n");
        sb.append("    }\n\n");

        sb.append("    public static boolean add(").append(keyType).append(" key, ")
                .append(entity.beanClassName).append(" value) {\n");
        sb.append("        Mdb mdb = Service.getCurrent().getMdb();\n");
        sb.append("        TTable<").append(keyType).append(", ").append(entity.beanClassName)
                .append("> tTable = mdb.getTTable(").append(entity.internalTableClassName).append(".class);\n");
        sb.append("        return tTable.add(key, value);\n");
        sb.append("    }\n\n");

        sb.append("    public static boolean add(").append(keyType).append(" key, ")
                .append(entity.beanClassName).append(" value, boolean immediately) {\n");
        sb.append("        Mdb mdb = Service.getCurrent().getMdb();\n");
        sb.append("        TTable<").append(keyType).append(", ").append(entity.beanClassName)
                .append("> tTable = mdb.getTTable(").append(entity.internalTableClassName).append(".class);\n");
        sb.append("        return tTable.add(key, value, immediately);\n");
        sb.append("    }\n\n");

        sb.append("    public static ").append(entity.beanClassName).append(" get(").append(keyType).append(" key) {\n");
        sb.append("        Mdb mdb = Service.getCurrent().getMdb();\n");
        sb.append("        TTable<").append(keyType).append(", ").append(entity.beanClassName)
                .append("> tTable = mdb.getTTable(").append(entity.internalTableClassName).append(".class);\n");
        sb.append("        return tTable.get(key);\n");
        sb.append("    }\n\n");

        sb.append("    public static boolean remove(").append(keyType).append(" key) {\n");
        sb.append("        Mdb mdb = Service.getCurrent().getMdb();\n");
        sb.append("        TTable<").append(keyType).append(", ").append(entity.beanClassName)
                .append("> tTable = mdb.getTTable(").append(entity.internalTableClassName).append(".class);\n");
        sb.append("        return tTable.remove(key);\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }
}
