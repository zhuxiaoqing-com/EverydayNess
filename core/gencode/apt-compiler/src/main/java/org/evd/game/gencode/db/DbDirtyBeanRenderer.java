package org.evd.game.gencode.db;

import org.evd.game.annotation.DBserialize;

final class DbDirtyBeanRenderer {
    private final DbDirtyFieldTagRenderer jsonTagRenderer = new DbDirtyJsonFieldTagRenderer();
    private final DbDirtyFieldTagRenderer pbTagRenderer = new DbDirtyPbFieldTagRenderer();
    private final DbDirtyFieldTagRenderer noopTagRenderer = new DbDirtyNoopFieldTagRenderer();

    String render(DbDirtyEntityMeta entity) {
        var importedEntityTypes = DbDirtyTypeNameSupport.collectImportedEntityTypes(entity);
        StringBuilder sb = new StringBuilder(8192);
        DbDirtyFieldTagRenderer tagRenderer = resolveTagRenderer(entity.dbType);
        sb.append("package ").append(entity.beanPackage).append(";\n\n");
        sb.append("import org.evd.game.base.DirtyObject;\n");
        tagRenderer.appendImport(sb, entity);
        DbDirtyTypeNameSupport.appendImports(sb, importedEntityTypes);
        if (entity.usesList()) {
            sb.append("import org.evd.game.runtime.Db.collection.XArrayList;\n");
        }
        if (entity.usesMap()) {
            sb.append("import org.evd.game.runtime.Db.collection.XHashMap;\n");
        }
        if (entity.usesSet()) {
            sb.append("import org.evd.game.runtime.Db.collection.XHashSet;\n");
        }
        sb.append("\n");

        sb.append("public final class ").append(entity.beanClassName)
                .append(" extends DirtyObject {\n");
        for (DbDirtyFieldMeta field : entity.fields) {
            tagRenderer.appendAnnotation(sb, field, "    ");
            sb.append("    private ").append(field.type.fieldType).append(" ").append(field.name).append(";\n");
        }
        sb.append("\n");
        appendParentConstructor(sb, entity);
        sb.append("\n");
        appendDefaultConstructor(sb, entity);
        sb.append("\n");
        appendPublicCopyConstructor(sb, entity);
        sb.append("\n");
        appendParentCopyConstructor(sb, entity);
        sb.append("\n");
        appendCopyFrom(sb, entity);
        sb.append("\n");
        appendGettersAndSetters(sb, entity);
        appendToString(sb, entity);
        sb.append("}\n");
        return DbDirtyTypeNameSupport.rewriteImportedTypeNames(sb.toString(), importedEntityTypes);
    }

    private DbDirtyFieldTagRenderer resolveTagRenderer(DBserialize dbType) {
        return switch (dbType) {
            case JSON -> jsonTagRenderer;
            case PB -> pbTagRenderer;
            default -> noopTagRenderer;
        };
    }

    private void appendParentConstructor(StringBuilder sb, DbDirtyEntityMeta entity) {
        sb.append("    ").append(entity.beanClassName).append("(DirtyObject _xp_) {\n");
        sb.append("        super(_xp_);\n");
        for (DbDirtyFieldMeta field : entity.fields) {
            String defaultValue = DbDirtyRenderSupport.renderDefaultValue(field.type, "this");
            if (defaultValue != null) {
                sb.append("        this.").append(field.name).append(" = ").append(defaultValue).append(";\n");
            }
        }
        sb.append("    }\n");
    }

    private void appendDefaultConstructor(StringBuilder sb, DbDirtyEntityMeta entity) {
        sb.append("    public ").append(entity.beanClassName).append("() {\n");
        sb.append("        this((DirtyObject)null);\n");
        sb.append("    }\n");
    }

    private void appendPublicCopyConstructor(StringBuilder sb, DbDirtyEntityMeta entity) {
        sb.append("    public ").append(entity.beanClassName).append("(").append(entity.beanClassName).append(" _o_) {\n");
        sb.append("        this(_o_, null);\n");
        sb.append("    }\n");
    }

    private void appendParentCopyConstructor(StringBuilder sb, DbDirtyEntityMeta entity) {
        // 公共 dbDef 生成的 bean 可能会被其他包下的 bean 组合使用，这个拷贝构造需要跨包可见。
        sb.append("    public ").append(entity.beanClassName).append("(").append(entity.beanClassName).append(" _o_, DirtyObject _xp_) {\n");
        sb.append("        super(_xp_);\n");
        for (DbDirtyFieldMeta field : entity.fields) {
            appendCopyField(sb, field, "        ", "_o_." + field.name, false);
        }
        sb.append("        this.dirty = false;\n");
        sb.append("    }\n");
    }

    private void appendCopyFrom(StringBuilder sb, DbDirtyEntityMeta entity) {
        sb.append("    public void copyFrom(").append(entity.beanClassName).append(" _o_) {\n");
        for (DbDirtyFieldMeta field : entity.fields) {
            appendCopyField(sb, field, "        ", "_o_." + field.name, true);
        }
        sb.append("        makeModify();\n");
        sb.append("    }\n");
    }

    private void appendCopyField(StringBuilder sb, DbDirtyFieldMeta field, String indent, String sourceExpr, boolean withModify) {
        DbDirtyTypeMeta type = field.type;
        switch (type.kind) {
            case LIST -> {
                sb.append(indent).append("this.").append(field.name).append(" = new XArrayList<>(this);\n");
                if (type.elementType.kind == DbDirtyTypeKind.ENTITY) {
                    sb.append(indent).append(sourceExpr).append(".forEach(_v_ -> {\n");
                    sb.append(indent).append("    ").append(type.elementType.fieldType)
                            .append(" _copy_ = ").append(copyValueExpr(type.elementType, "_v_", "this." + field.name)).append(";\n");
                    sb.append(indent).append("    this.").append(field.name).append(".add(_copy_);\n");
                    sb.append(indent).append("});\n");
                } else {
                    sb.append(indent).append("this.").append(field.name).append(".addAll(").append(sourceExpr).append(");\n");
                }
            }
            case SET -> {
                sb.append(indent).append("this.").append(field.name).append(" = new XHashSet<>(this);\n");
                if (type.elementType.kind == DbDirtyTypeKind.ENTITY) {
                    sb.append(indent).append(sourceExpr).append(".forEach(_v_ -> {\n");
                    sb.append(indent).append("    ").append(type.elementType.fieldType)
                            .append(" _copy_ = ").append(copyValueExpr(type.elementType, "_v_", "this." + field.name)).append(";\n");
                    sb.append(indent).append("    this.").append(field.name).append(".add(_copy_);\n");
                    sb.append(indent).append("});\n");
                } else {
                    sb.append(indent).append("this.").append(field.name).append(".addAll(").append(sourceExpr).append(");\n");
                }
            }
            case MAP -> {
                sb.append(indent).append("this.").append(field.name).append(" = new XHashMap<>(this);\n");
                if (type.keyType.kind == DbDirtyTypeKind.ENTITY || type.valueType.kind == DbDirtyTypeKind.ENTITY) {
                    sb.append(indent).append(sourceExpr).append(".forEach((_k_, _v_) -> this.").append(field.name).append(".put(")
                            .append(copyValueExpr(type.keyType, "_k_", "this." + field.name)).append(", ")
                            .append(copyValueExpr(type.valueType, "_v_", "this." + field.name)).append("));\n");
                } else {
                    sb.append(indent).append("this.").append(field.name).append(".putAll(").append(sourceExpr).append(");\n");
                }
            }
            case ENTITY -> sb.append(indent).append("this.").append(field.name).append(" = ")
                    .append(copyValueExpr(type, sourceExpr, "this")).append(";\n");
            default -> sb.append(indent).append("this.").append(field.name).append(" = ").append(sourceExpr).append(";\n");
        }
        if (withModify && type.kind == DbDirtyTypeKind.ENTITY) {
            sb.append(indent).append("if (this.").append(field.name).append(" != null) {\n");
            sb.append(indent).append("    this.").append(field.name).append(".setParent(this);\n");
            sb.append(indent).append("}\n");
        }
    }

    private String copyValueExpr(DbDirtyTypeMeta type, String valueExpr, String parentExpr) {
        if (type.kind == DbDirtyTypeKind.ENTITY) {
            return valueExpr + " == null ? null : new " + type.fieldType + "(" + valueExpr + ", " + parentExpr + ")";
        }
        return valueExpr;
    }

    private void appendGettersAndSetters(StringBuilder sb, DbDirtyEntityMeta entity) {
        for (DbDirtyFieldMeta field : entity.fields) {
            sb.append("    public ").append(field.type.getterType).append(" get").append(field.methodSuffix).append("(){\n");
            sb.append("        return this.").append(field.name).append(";\n");
            sb.append("    }\n\n");

            sb.append("    public void set").append(field.methodSuffix).append("(").append(field.type.fieldType).append(" _v_){\n");
            sb.append("        this.").append(field.name).append(" = _v_;\n");
            if (field.type.kind == DbDirtyTypeKind.ENTITY || field.type.kind == DbDirtyTypeKind.LIST
                    || field.type.kind == DbDirtyTypeKind.SET || field.type.kind == DbDirtyTypeKind.MAP) {
                sb.append("        if (_v_ != null) {\n");
                sb.append("            _v_.setParent(this);\n");
                sb.append("        }\n");
            }
            sb.append("        makeModify();\n");
            sb.append("    }\n\n");
        }
    }

    private void appendToString(StringBuilder sb, DbDirtyEntityMeta entity) {
        sb.append("    @Override\n");
        sb.append("    public String toString() {\n");
        sb.append("        StringBuilder _sb_ = new StringBuilder(super.toString());\n");
        sb.append("        _sb_.append(\"=(\");\n");
        for (DbDirtyFieldMeta field : entity.fields) {
            sb.append("        _sb_.append(\"").append(field.name).append("=\").append(");
            if (field.type.kind == DbDirtyTypeKind.STRING) {
                sb.append(field.name).append(" == null ? \"null\" : \"T\" + ").append(field.name).append(".length()");
            } else {
                sb.append(field.name);
            }
            sb.append(").append(\",\");\n");
        }
        sb.append("        _sb_.append(\")\");\n");
        sb.append("        return _sb_.toString();\n");
        sb.append("    }\n");
    }
}

interface DbDirtyFieldTagRenderer {
    void appendImport(StringBuilder sb, DbDirtyEntityMeta entity);

    void appendAnnotation(StringBuilder sb, DbDirtyFieldMeta field, String indent);
}

final class DbDirtyNoopFieldTagRenderer implements DbDirtyFieldTagRenderer {
    @Override
    public void appendImport(StringBuilder sb, DbDirtyEntityMeta entity) {
    }

    @Override
    public void appendAnnotation(StringBuilder sb, DbDirtyFieldMeta field, String indent) {
    }
}

final class DbDirtyJsonFieldTagRenderer implements DbDirtyFieldTagRenderer {
    @Override
    public void appendImport(StringBuilder sb, DbDirtyEntityMeta entity) {
        if (entity.hasTaggedField()) {
            sb.append("import com.alibaba.fastjson2.annotation.JSONField;\n");
        }
    }

    @Override
    public void appendAnnotation(StringBuilder sb, DbDirtyFieldMeta field, String indent) {
        if (field.tagValue != null) {
            sb.append(indent).append("@JSONField(name = \"").append(field.tagValue).append("\")\n");
        }
    }
}

final class DbDirtyPbFieldTagRenderer implements DbDirtyFieldTagRenderer {
    @Override
    public void appendImport(StringBuilder sb, DbDirtyEntityMeta entity) {
        if (entity.hasTaggedField()) {
            sb.append("import io.protostuff.Tag;\n");
        }
    }

    @Override
    public void appendAnnotation(StringBuilder sb, DbDirtyFieldMeta field, String indent) {
        if (field.tagValue != null) {
            sb.append(indent).append("@Tag(").append(field.tagValue).append(")\n");
        }
    }
}
