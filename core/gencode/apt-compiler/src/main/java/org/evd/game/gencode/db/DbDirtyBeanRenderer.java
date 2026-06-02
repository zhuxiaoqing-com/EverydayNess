package org.evd.game.gencode.db;

import org.evd.game.annotation.DBserialize;

import java.util.concurrent.atomic.AtomicInteger;

final class DbDirtyBeanRenderer {
    private final DbDirtyFieldTagRenderer jsonTagRenderer = new DbDirtyJsonFieldTagRenderer();
    private final DbDirtyFieldTagRenderer pbTagRenderer = new DbDirtyPbFieldTagRenderer();
    private final DbDirtyFieldTagRenderer noopTagRenderer = new DbDirtyNoopFieldTagRenderer();

    String render(DbDirtyEntityMeta entity) {
        StringBuilder sb = new StringBuilder(8192);
        DbDirtyFieldTagRenderer tagRenderer = resolveTagRenderer(entity.dbType);
        sb.append("package ").append(entity.beanPackage).append(";\n\n");
        sb.append("import org.evd.game.annotation.SerializeClass;\n");
        sb.append("import org.evd.game.base.DirtyObject;\n");
        sb.append("import org.evd.game.base.ISerializable;\n");
        sb.append("import org.evd.game.base.InputStreamBase;\n");
        sb.append("import org.evd.game.base.OutputStreamBase;\n");
        tagRenderer.appendImport(sb, entity);
        if (entity.usesList()) {
            sb.append("import org.evd.game.runtime.Db.collection.XArrayList;\n");
        }
        if (entity.usesMap()) {
            sb.append("import org.evd.game.runtime.Db.collection.XHashMap;\n");
        }
        if (entity.usesSet()) {
            sb.append("import org.evd.game.runtime.Db.collection.XHashSet;\n");
        }
        sb.append("import java.io.IOException;\n\n");

        sb.append("@SerializeClass(customized = true)\n");
        sb.append("public final class ").append(entity.beanClassName)
                .append(" extends DirtyObject implements ISerializable {\n");
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
        appendWriteTo(sb, entity);
        sb.append("\n");
        appendReadFrom(sb, entity);
        sb.append("\n");
        appendToString(sb, entity);
        sb.append("}\n");
        return sb.toString();
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
        sb.append("    ").append(entity.beanClassName).append("(").append(entity.beanClassName).append(" _o_, DirtyObject _xp_) {\n");
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

    private void appendWriteTo(StringBuilder sb, DbDirtyEntityMeta entity) {
        sb.append("    @Override\n");
        sb.append("    public void writeTo(OutputStreamBase out) throws IOException {\n");
        AtomicInteger seq = new AtomicInteger();
        for (DbDirtyFieldMeta field : entity.fields) {
            appendWriteValue(sb, field.type, "this." + field.name, "out", "        ", seq);
        }
        sb.append("    }\n");
    }

    private void appendReadFrom(StringBuilder sb, DbDirtyEntityMeta entity) {
        sb.append("    @Override\n");
        sb.append("    public void readFrom(InputStreamBase in) throws IOException {\n");
        AtomicInteger seq = new AtomicInteger();
        for (DbDirtyFieldMeta field : entity.fields) {
            String valueVar = appendReadValue(sb, field.type, "in", "        ", "this", seq);
            sb.append("        this.").append(field.name).append(" = ").append(valueVar).append(";\n");
        }
        sb.append("        this.dirty = false;\n");
        sb.append("    }\n");
    }

    private void appendWriteValue(StringBuilder sb, DbDirtyTypeMeta type, String valueExpr, String outExpr, String indent, AtomicInteger seq) {
        switch (type.kind) {
            case PRIMITIVE -> sb.append(indent).append(DbDirtyRenderSupport.renderPrimitiveWrite(type.fieldType, outExpr, valueExpr)).append("\n");
            case STRING -> sb.append(indent).append(outExpr).append(".writeString(").append(valueExpr).append(");\n");
            case OTHER -> sb.append(indent).append(outExpr).append(".write(").append(valueExpr).append(");\n");
            case ENTITY -> {
                sb.append(indent).append(outExpr).append(".writeBoolean(").append(valueExpr).append(" != null);\n");
                sb.append(indent).append("if (").append(valueExpr).append(" != null) {\n");
                sb.append(indent).append("    ").append(valueExpr).append(".beforeWrite(").append(outExpr).append(");\n");
                sb.append(indent).append("    ").append(valueExpr).append(".writeTo(").append(outExpr).append(");\n");
                sb.append(indent).append("    ").append(valueExpr).append(".afterWrite(").append(outExpr).append(");\n");
                sb.append(indent).append("}\n");
            }
            case LIST, SET -> {
                String itemVar = "_v_" + seq.getAndIncrement();
                sb.append(indent).append("if (").append(valueExpr).append(" == null) {\n");
                sb.append(indent).append("    ").append(outExpr).append(".writeInt(-1);\n");
                sb.append(indent).append("} else {\n");
                sb.append(indent).append("    ").append(outExpr).append(".writeInt(").append(valueExpr).append(".size());\n");
                sb.append(indent).append("    for (").append(type.elementType.boxedType()).append(" ").append(itemVar)
                        .append(" : ").append(valueExpr).append(") {\n");
                appendWriteValue(sb, type.elementType, itemVar, outExpr, indent + "        ", seq);
                sb.append(indent).append("    }\n");
                sb.append(indent).append("}\n");
            }
            case MAP -> {
                String entryVar = "_entry_" + seq.getAndIncrement();
                sb.append(indent).append("if (").append(valueExpr).append(" == null) {\n");
                sb.append(indent).append("    ").append(outExpr).append(".writeInt(-1);\n");
                sb.append(indent).append("} else {\n");
                sb.append(indent).append("    ").append(outExpr).append(".writeInt(").append(valueExpr).append(".size());\n");
                sb.append(indent).append("    for (java.util.Map.Entry<").append(type.keyType.boxedType()).append(", ")
                        .append(type.valueType.boxedType()).append("> ").append(entryVar).append(" : ")
                        .append(valueExpr).append(".entrySet()) {\n");
                appendWriteValue(sb, type.keyType, entryVar + ".getKey()", outExpr, indent + "        ", seq);
                appendWriteValue(sb, type.valueType, entryVar + ".getValue()", outExpr, indent + "        ", seq);
                sb.append(indent).append("    }\n");
                sb.append(indent).append("}\n");
            }
        }
    }

    private String appendReadValue(StringBuilder sb, DbDirtyTypeMeta type, String inExpr, String indent, String parentExpr, AtomicInteger seq) {
        String varName = "_v_" + seq.getAndIncrement();
        switch (type.kind) {
            case PRIMITIVE -> sb.append(indent).append(type.fieldType).append(" ").append(varName).append(" = ")
                    .append(DbDirtyRenderSupport.renderPrimitiveRead(type.fieldType, inExpr)).append(";\n");
            case STRING -> sb.append(indent).append("String ").append(varName).append(" = ").append(inExpr).append(".readString();\n");
            case OTHER -> sb.append(indent).append(type.fieldType).append(" ").append(varName)
                    .append(" = ").append(DbDirtyRenderSupport.renderObjectRead(type.fieldType, inExpr)).append(";\n");
            case ENTITY -> {
                sb.append(indent).append(type.fieldType).append(" ").append(varName).append(" = null;\n");
                sb.append(indent).append("if (").append(inExpr).append(".readBoolean()) {\n");
                sb.append(indent).append("    ").append(varName).append(" = new ").append(type.fieldType).append("();\n");
                sb.append(indent).append("    ").append(varName).append(".beforeRead(").append(inExpr).append(");\n");
                sb.append(indent).append("    ").append(varName).append(".readFrom(").append(inExpr).append(");\n");
                sb.append(indent).append("    ").append(varName).append(".afterRead(").append(inExpr).append(");\n");
                if (parentExpr != null) {
                    sb.append(indent).append("    ").append(varName).append(".setParent(").append(parentExpr).append(");\n");
                }
                sb.append(indent).append("}\n");
            }
            case LIST -> {
                String sizeVar = "_size_" + seq.getAndIncrement();
                sb.append(indent).append("XArrayList<").append(type.elementType.boxedType()).append("> ")
                        .append(varName).append(" = null;\n");
                sb.append(indent).append("int ").append(sizeVar).append(" = ").append(inExpr).append(".readInt();\n");
                sb.append(indent).append("if (").append(sizeVar).append(" >= 0) {\n");
                sb.append(indent).append("    ").append(varName).append(" = new XArrayList<>(").append(parentExpr).append(");\n");
                sb.append(indent).append("    for (int _i_ = 0; _i_ < ").append(sizeVar).append("; _i_++) {\n");
                String childVar = appendReadValue(sb, type.elementType, inExpr, indent + "        ", varName, seq);
                sb.append(indent).append("        ").append(varName).append(".add(").append(childVar).append(");\n");
                sb.append(indent).append("    }\n");
                sb.append(indent).append("}\n");
            }
            case SET -> {
                String sizeVar = "_size_" + seq.getAndIncrement();
                sb.append(indent).append("XHashSet<").append(type.elementType.boxedType()).append("> ")
                        .append(varName).append(" = null;\n");
                sb.append(indent).append("int ").append(sizeVar).append(" = ").append(inExpr).append(".readInt();\n");
                sb.append(indent).append("if (").append(sizeVar).append(" >= 0) {\n");
                sb.append(indent).append("    ").append(varName).append(" = new XHashSet<>(").append(parentExpr).append(");\n");
                sb.append(indent).append("    for (int _i_ = 0; _i_ < ").append(sizeVar).append("; _i_++) {\n");
                String childVar = appendReadValue(sb, type.elementType, inExpr, indent + "        ", varName, seq);
                sb.append(indent).append("        ").append(varName).append(".add(").append(childVar).append(");\n");
                sb.append(indent).append("    }\n");
                sb.append(indent).append("}\n");
            }
            case MAP -> {
                String sizeVar = "_size_" + seq.getAndIncrement();
                sb.append(indent).append("XHashMap<").append(type.keyType.boxedType()).append(", ")
                        .append(type.valueType.boxedType()).append("> ").append(varName).append(" = null;\n");
                sb.append(indent).append("int ").append(sizeVar).append(" = ").append(inExpr).append(".readInt();\n");
                sb.append(indent).append("if (").append(sizeVar).append(" >= 0) {\n");
                sb.append(indent).append("    ").append(varName).append(" = new XHashMap<>(").append(parentExpr).append(");\n");
                sb.append(indent).append("    for (int _i_ = 0; _i_ < ").append(sizeVar).append("; _i_++) {\n");
                String keyVar = appendReadValue(sb, type.keyType, inExpr, indent + "        ", varName, seq);
                String valueVar = appendReadValue(sb, type.valueType, inExpr, indent + "        ", varName, seq);
                sb.append(indent).append("        ").append(varName).append(".put(").append(keyVar).append(", ")
                        .append(valueVar).append(");\n");
                sb.append(indent).append("    }\n");
                sb.append(indent).append("}\n");
            }
        }
        return varName;
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
