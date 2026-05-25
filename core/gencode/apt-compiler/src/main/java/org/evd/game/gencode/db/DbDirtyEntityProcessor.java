package org.evd.game.gencode.db;

import com.google.auto.service.AutoService;
import org.evd.game.annotation.DBDirtyEntity;
import org.evd.game.annotation.DBDirtyTag;
import org.evd.game.annotation.DBserialize;
import org.evd.game.gencode.ProcessorBase;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

@AutoService(Processor.class)
public class DbDirtyEntityProcessor extends ProcessorBase {
    private static final String DATA_DEF_SUFFIX = "DataDef";
    private static final String DB_ENTITY_PACKAGE_SUFFIX = ".dbEntity";
    private static final String DB_PACKAGE_SUFFIX = ".db";

    @Override
    protected Set<String> supportAnnotation() {
        return Collections.singleton(DBDirtyEntity.class.getCanonicalName());
    }

    @Override
    protected void init() {

    }

    @Override
    protected void gen(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        println("");
        println("开始执行DBDirtyEntity Processor");

        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(DBDirtyEntity.class);
        if (elements == null || elements.isEmpty()) {
            return;
        }

        for (Element element : elements) {
            if (!(element instanceof TypeElement typeElement)) {
                continue;
            }
            EntityModel entity = EntityModel.of(typeElement, processingEnv);
            writeEntity(entity);
        }
    }

    private void writeEntity(EntityModel entity) {
        Path javaFile = resolveGenJavaFile(entity);
        try {
            Files.createDirectories(javaFile.getParent());
            Files.writeString(javaFile, renderEntity(entity), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("生成DB实体失败: " + javaFile, e);
        }
    }

    private Path resolveGenJavaFile(EntityModel entity) {
        try {
            String sourceOutputUri = filer.getResource(StandardLocation.SOURCE_OUTPUT, "", entity.className + ".java")
                    .toUri()
                    .toString();
            String normalizedPath = sourceOutputUri.replace('\\', '/');
            String sourceOutputPath = normalizedPath.startsWith("file:/")
                    ? Paths.get(java.net.URI.create(sourceOutputUri)).toString()
                    : sourceOutputUri;

            String normalizedFsPath = sourceOutputPath.replace('\\', '/');
            int buildIndex = normalizedFsPath.indexOf("/build/");
            if (buildIndex < 0) {
                throw new IllegalStateException("无法从 SOURCE_OUTPUT 推导模块根目录: " + sourceOutputPath);
            }

            String moduleRoot = sourceOutputPath.substring(0, buildIndex);
            Path genRoot = Paths.get(moduleRoot, "src", "gen", "java");
            Path packagePath = Paths.get(entity.targetPackage.replace(".", java.io.File.separator));
            return genRoot.resolve(packagePath).resolve(entity.className + ".java");
        } catch (IOException e) {
            throw new RuntimeException("解析生成目录失败", e);
        }
    }

    private String renderEntity(EntityModel entity) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("package ").append(entity.targetPackage).append(";\n");
        appendSerializationImport(sb, entity);
        sb.append("import org.evd.game.base.DirtyObject;\n");
        if (entity.usesList()) {
            sb.append("import org.evd.game.runtime.DbEntity.collection.XArrayList;\n");
        }
        if (entity.usesMap()) {
            sb.append("import org.evd.game.runtime.DbEntity.collection.XHashMap;\n");
        }
        if (entity.usesSet()) {
            sb.append("import org.evd.game.runtime.DbEntity.collection.XHashSet;\n");
        }
        sb.append("\n");
        sb.append("public final class ").append(entity.className).append(" extends DirtyObject {\n");
        for (FieldModel field : entity.fields) {
            appendFieldAnnotation(sb, entity, field, "    ");
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
        return sb.toString();
    }

    private void appendParentConstructor(StringBuilder sb, EntityModel entity) {
        sb.append("    ").append(entity.className).append("(DirtyObject _xp_) {\n");
        sb.append("        super(_xp_);\n");
        for (FieldModel field : entity.fields) {
            if (field.type.initExpr != null) {
                sb.append("        this.").append(field.name).append(" = ").append(field.type.initExpr).append(";\n");
            }
        }
        sb.append("    }\n");
    }

    private void appendDefaultConstructor(StringBuilder sb, EntityModel entity) {
        sb.append("    public ").append(entity.className).append("() {\n");
        sb.append("        this((DirtyObject)null);\n");
        sb.append("    }\n");
    }

    private void appendPublicCopyConstructor(StringBuilder sb, EntityModel entity) {
        sb.append("    public ").append(entity.className).append("(").append(entity.className).append(" _o_) {\n");
        sb.append("        this(_o_, null);\n");
        sb.append("    }\n");
    }

    private void appendParentCopyConstructor(StringBuilder sb, EntityModel entity) {
        sb.append("    ").append(entity.className).append("(").append(entity.className).append(" _o_, DirtyObject _xp_) {\n");
        sb.append("        super(_xp_);\n");
        for (FieldModel field : entity.fields) {
            appendCopyField(sb, field, "        ", "_o_." + field.name, false);
        }
        sb.append("        this.dirty = false;\n");
        sb.append("    }\n");
    }

    private void appendCopyFrom(StringBuilder sb, EntityModel entity) {
        sb.append("    public void copyFrom(").append(entity.className).append(" _o_) {\n");
        for (FieldModel field : entity.fields) {
            appendCopyField(sb, field, "        ", "_o_." + field.name, true);
        }
        sb.append("        makeModify();\n");
        sb.append("    }\n");
    }

    private void appendCopyField(StringBuilder sb, FieldModel field, String indent, String sourceExpr, boolean withModify) {
        TypeModel type = field.type;
        switch (type.kind) {
            case LIST -> {
                sb.append(indent).append("this.").append(field.name).append(" = ").append(type.newCollectionExpr).append(";\n");
                if (type.elementType.kind == TypeKind.ENTITY) {
                    sb.append(indent).append(sourceExpr).append(".forEach(_v_ -> this.").append(field.name)
                            .append(".add(").append(copyValueExpr(type.elementType, "_v_", "this." + field.name)).append("));\n");
                } else {
                    sb.append(indent).append("this.").append(field.name).append(".addAll(").append(sourceExpr).append(");\n");
                }
            }
            case SET -> {
                sb.append(indent).append("this.").append(field.name).append(" = ").append(type.newCollectionExpr).append(";\n");
                if (type.elementType.kind == TypeKind.ENTITY) {
                    sb.append(indent).append(sourceExpr).append(".forEach(_v_ -> this.").append(field.name)
                            .append(".add(").append(copyValueExpr(type.elementType, "_v_", "this." + field.name)).append("));\n");
                } else {
                    sb.append(indent).append("this.").append(field.name).append(".addAll(").append(sourceExpr).append(");\n");
                }
            }
            case MAP -> {
                sb.append(indent).append("this.").append(field.name).append(" = ").append(type.newCollectionExpr).append(";\n");
                if (type.keyType.kind == TypeKind.ENTITY || type.valueType.kind == TypeKind.ENTITY) {
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
        if (withModify && type.kind == TypeKind.ENTITY) {
            sb.append(indent).append("if (this.").append(field.name).append(" != null) {\n");
            sb.append(indent).append("    this.").append(field.name).append(".setParent(this);\n");
            sb.append(indent).append("}\n");
        }
    }

    private String copyValueExpr(TypeModel type, String valueExpr, String parentExpr) {
        if (type.kind == TypeKind.ENTITY) {
            return valueExpr + " == null ? null : new " + type.fieldType + "(" + valueExpr + ", " + parentExpr + ")";
        }
        return valueExpr;
    }

    private void appendGettersAndSetters(StringBuilder sb, EntityModel entity) {
        for (FieldModel field : entity.fields) {
            sb.append("    public ").append(field.type.getterType).append(" get").append(field.methodSuffix).append("(){\n");
            sb.append("        return this.").append(field.name).append(";\n");
            sb.append("    }\n\n");

            sb.append("    public void set").append(field.methodSuffix).append("(").append(field.type.fieldType).append(" _v_){\n");
            sb.append("        this.").append(field.name).append(" = _v_;\n");
            if (field.type.kind == TypeKind.ENTITY || field.type.kind == TypeKind.LIST
                    || field.type.kind == TypeKind.SET || field.type.kind == TypeKind.MAP) {
                sb.append("        if (_v_ != null) {\n");
                sb.append("            _v_.setParent(this);\n");
                sb.append("        }\n");
            }
            sb.append("        makeModify();\n");
            sb.append("    }\n\n");
        }
    }

    private void appendSerializationImport(StringBuilder sb, EntityModel entity) {
        if (!entity.hasTaggedField()) {
            return;
        }
        if (entity.dbType == DBserialize.JSON) {
            sb.append("import com.alibaba.fastjson2.annotation.JSONField;\n");
        } else if (entity.dbType == DBserialize.PB) {
            sb.append("import io.protostuff.Tag;\n");
        }
    }

    private void appendFieldAnnotation(StringBuilder sb, EntityModel entity, FieldModel field, String indent) {
        if (field.tagValue == null) {
            return;
        }
        if (entity.dbType == DBserialize.JSON) {
            sb.append(indent).append("@JSONField(name = \"").append(field.tagValue).append("\")\n");
        } else if (entity.dbType == DBserialize.PB) {
            sb.append(indent).append("@Tag(").append(field.tagValue).append(")\n");
        }
    }

    private void appendToString(StringBuilder sb, EntityModel entity) {
        sb.append("    @Override\n");
        sb.append("    public String toString() {\n");
        sb.append("        StringBuilder _sb_ = new StringBuilder(super.toString());\n");
        sb.append("        _sb_.append(\"=(\");\n");
        for (FieldModel field : entity.fields) {
            sb.append("        _sb_.append(\"").append(field.name).append("=\").append(");
            if (field.type.kind == TypeKind.STRING) {
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

    private static String toTargetPackage(String sourcePackage) {
        if (sourcePackage.endsWith(DB_ENTITY_PACKAGE_SUFFIX)) {
            return sourcePackage.substring(0, sourcePackage.length() - DB_ENTITY_PACKAGE_SUFFIX.length()) + DB_PACKAGE_SUFFIX;
        }
        return sourcePackage + DB_PACKAGE_SUFFIX;
    }

    private static String toGeneratedClassName(String sourceClassName) {
        if (sourceClassName.endsWith(DATA_DEF_SUFFIX)) {
            return sourceClassName.substring(0, sourceClassName.length() - DATA_DEF_SUFFIX.length());
        }
        return sourceClassName;
    }

    private static String shortJavaLang(String typeName) {
        return typeName.replace("java.lang.", "");
    }

    private static final class EntityModel {
        private final String targetPackage;
        private final String className;
        private final DBserialize dbType;
        private final boolean table;
        private final List<FieldModel> fields;

        private EntityModel(String targetPackage, String className, DBserialize dbType, boolean table, List<FieldModel> fields) {
            this.targetPackage = targetPackage;
            this.className = className;
            this.dbType = dbType;
            this.table = table;
            this.fields = fields;
        }

        private static EntityModel of(TypeElement typeElement, ProcessingEnvironment processingEnv) {
            String sourcePackage = processingEnv.getElementUtils().getPackageOf(typeElement).getQualifiedName().toString();
            String targetPackage = toTargetPackage(sourcePackage);
            String className = toGeneratedClassName(typeElement.getSimpleName().toString());
            DBDirtyEntity dbDirtyEntity = typeElement.getAnnotation(DBDirtyEntity.class);
            DBserialize dbType = dbDirtyEntity.value();
            boolean table = dbDirtyEntity.table();
            List<FieldModel> fields = new ArrayList<>();
            for (Element element : typeElement.getEnclosedElements()) {
                if (element instanceof VariableElement variableElement) {
                    fields.add(FieldModel.of(variableElement, processingEnv, targetPackage, dbType, className));
                }
            }
            validateFields(className, table, fields);
            return new EntityModel(targetPackage, className, dbType, table, fields);
        }

        private static void validateFields(String className, boolean table, List<FieldModel> fields) {
            Set<Integer> usedTagValues = new HashSet<>();
            boolean hasPrimaryKey = false;
            for (FieldModel field : fields) {
                if (field.tagValue == null) {
                    continue;
                }
                if (field.tagValue <= 0) {
                    throw new IllegalStateException("DBDirtyTag.value 必须 > 0: " + className + "." + field.name);
                }
                if (!usedTagValues.add(field.tagValue)) {
                    throw new IllegalStateException("DBDirtyTag.value 不能重复: " + className + "." + field.name + " = " + field.tagValue);
                }
                if (field.primaryKey) {
                    hasPrimaryKey = true;
                    if (!field.type.supportPrimaryKey()) {
                        throw new IllegalStateException("primaryKey 只能是基础类型或String: " + className + "." + field.name);
                    }
                }
            }
            if (table && !hasPrimaryKey) {
                throw new IllegalStateException("table=true 的实体必须至少声明一个 primaryKey: " + className);
            }
        }

        private boolean usesList() {
            return fields.stream().anyMatch(field -> field.type.containsKind(TypeKind.LIST));
        }

        private boolean usesMap() {
            return fields.stream().anyMatch(field -> field.type.containsKind(TypeKind.MAP));
        }

        private boolean usesSet() {
            return fields.stream().anyMatch(field -> field.type.containsKind(TypeKind.SET));
        }

        private boolean hasTaggedField() {
            return fields.stream().anyMatch(field -> field.tagValue != null);
        }
    }

    private static final class FieldModel {
        private final String name;
        private final String methodSuffix;
        private final Integer tagValue;
        private final boolean primaryKey;
        private final TypeModel type;

        private FieldModel(String name, String methodSuffix, Integer tagValue, boolean primaryKey, TypeModel type) {
            this.name = name;
            this.methodSuffix = methodSuffix;
            this.tagValue = tagValue;
            this.primaryKey = primaryKey;
            this.type = type;
        }

        private static FieldModel of(VariableElement field, ProcessingEnvironment processingEnv, String currentTargetPackage,
                                     DBserialize ownerSerialize, String ownerClassName) {
            DBDirtyTag dbDirtyTag = field.getAnnotation(DBDirtyTag.class);
            Integer tagValue = dbDirtyTag == null ? null : dbDirtyTag.value();
            boolean primaryKey = dbDirtyTag != null && dbDirtyTag.primaryKey();
            String name = field.getSimpleName().toString();
            return new FieldModel(name, upperFirst(name), tagValue, primaryKey,
                    TypeModel.of(field.asType(), processingEnv, currentTargetPackage, ownerSerialize, ownerClassName, name));
        }

        private static String upperFirst(String name) {
            if (name == null || name.isEmpty()) {
                return name;
            }
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
    }

    private static final class TypeModel {
        private final TypeKind kind;
        private final String fieldType;
        private final String getterType;
        private final String initExpr;
        private final String newCollectionExpr;
        private final TypeModel elementType;
        private final TypeModel keyType;
        private final TypeModel valueType;

        private TypeModel(TypeKind kind, String fieldType, String getterType, String initExpr, String newCollectionExpr,
                          TypeModel elementType, TypeModel keyType, TypeModel valueType) {
            this.kind = kind;
            this.fieldType = fieldType;
            this.getterType = getterType;
            this.initExpr = initExpr;
            this.newCollectionExpr = newCollectionExpr;
            this.elementType = elementType;
            this.keyType = keyType;
            this.valueType = valueType;
        }

        private static TypeModel of(TypeMirror typeMirror, ProcessingEnvironment processingEnv, String currentTargetPackage,
                                    DBserialize ownerSerialize, String ownerClassName, String fieldName) {
            if (typeMirror.getKind().isPrimitive()) {
                String typeName = typeMirror.toString();
                return new TypeModel(TypeKind.PRIMITIVE, typeName, typeName, null, null, null, null, null);
            }

            if (typeMirror instanceof DeclaredType declaredType) {
                TypeElement typeElement = (TypeElement) declaredType.asElement();
                String qualifiedName = typeElement.getQualifiedName().toString();
                if (qualifiedName.equals(String.class.getCanonicalName())) {
                    return new TypeModel(TypeKind.STRING, "String", "String", "\"\"", null, null, null, null);
                }
                if (qualifiedName.equals(List.class.getCanonicalName())) {
                    TypeModel elementType = of(declaredType.getTypeArguments().get(0), processingEnv, currentTargetPackage,
                            ownerSerialize, ownerClassName, fieldName);
                    String genericType = elementType.fieldType;
                    return new TypeModel(TypeKind.LIST,
                            "XArrayList<" + genericType + ">",
                            "java.util.List<" + genericType + ">",
                            "new XArrayList<>(this)",
                            "new XArrayList<>(this)",
                            elementType, null, null);
                }
                if (qualifiedName.equals(Set.class.getCanonicalName())) {
                    TypeModel elementType = of(declaredType.getTypeArguments().get(0), processingEnv, currentTargetPackage,
                            ownerSerialize, ownerClassName, fieldName);
                    String genericType = elementType.fieldType;
                    return new TypeModel(TypeKind.SET,
                            "XHashSet<" + genericType + ">",
                            "java.util.Set<" + genericType + ">",
                            "new XHashSet<>(this)",
                            "new XHashSet<>(this)",
                            elementType, null, null);
                }
                if (qualifiedName.equals(java.util.Map.class.getCanonicalName())) {
                    TypeModel keyType = of(declaredType.getTypeArguments().get(0), processingEnv, currentTargetPackage,
                            ownerSerialize, ownerClassName, fieldName);
                    TypeModel valueType = of(declaredType.getTypeArguments().get(1), processingEnv, currentTargetPackage,
                            ownerSerialize, ownerClassName, fieldName);
                    return new TypeModel(TypeKind.MAP,
                            "XHashMap<" + keyType.fieldType + ", " + valueType.fieldType + ">",
                            "java.util.Map<" + keyType.fieldType + ", " + valueType.fieldType + ">",
                            "new XHashMap<>(this)",
                            "new XHashMap<>(this)",
                            null, keyType, valueType);
                }
                DBDirtyEntity childEntity = typeElement.getAnnotation(DBDirtyEntity.class);
                if (childEntity != null) {
                    if (childEntity.value() != ownerSerialize) {
                        throw new IllegalStateException("父子 DBserialize 必须一致: " + ownerClassName + "." + fieldName
                                + " -> " + typeElement.getQualifiedName() + "，父=" + ownerSerialize + "，子=" + childEntity.value());
                    }
                    String typeName = renderDbEntityType(typeElement, processingEnv, currentTargetPackage);
                    return new TypeModel(TypeKind.ENTITY, typeName, typeName, null, null, null, null, null);
                }
                String typeName = renderDeclaredType(declaredType, processingEnv, currentTargetPackage,
                        ownerSerialize, ownerClassName, fieldName);
                return new TypeModel(TypeKind.OTHER, typeName, typeName, null, null, null, null, null);
            }

            String typeName = shortJavaLang(typeMirror.toString());
            return new TypeModel(TypeKind.OTHER, typeName, typeName, null, null, null, null, null);
        }

        private boolean containsKind(TypeKind targetKind) {
            if (kind == targetKind) {
                return true;
            }
            return (elementType != null && elementType.containsKind(targetKind))
                    || (keyType != null && keyType.containsKind(targetKind))
                    || (valueType != null && valueType.containsKind(targetKind));
        }

        private boolean supportPrimaryKey() {
            return kind == TypeKind.PRIMITIVE || kind == TypeKind.STRING;
        }

        private static String renderDbEntityType(TypeElement typeElement, ProcessingEnvironment processingEnv, String currentTargetPackage) {
            String sourcePackage = processingEnv.getElementUtils().getPackageOf(typeElement).getQualifiedName().toString();
            String targetPackage = toTargetPackage(sourcePackage);
            String className = toGeneratedClassName(typeElement.getSimpleName().toString());
            if (targetPackage.equals(currentTargetPackage)) {
                return className;
            }
            return targetPackage + "." + className;
        }

        private static String renderDeclaredType(DeclaredType declaredType, ProcessingEnvironment processingEnv,
                                                 String currentTargetPackage, DBserialize ownerSerialize,
                                                 String ownerClassName, String fieldName) {
            TypeElement typeElement = (TypeElement) declaredType.asElement();
            String qualifiedName = typeElement.getQualifiedName().toString();
            List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
            if (typeArguments.isEmpty()) {
                return shortJavaLang(qualifiedName);
            }
            StringJoiner joiner = new StringJoiner(", ");
            for (TypeMirror typeArgument : typeArguments) {
                joiner.add(of(typeArgument, processingEnv, currentTargetPackage,
                        ownerSerialize, ownerClassName, fieldName).fieldType);
            }
            return shortJavaLang(qualifiedName) + "<" + joiner + ">";
        }
    }

    private enum TypeKind {
        PRIMITIVE,
        STRING,
        ENTITY,
        LIST,
        SET,
        MAP,
        OTHER
    }
}
