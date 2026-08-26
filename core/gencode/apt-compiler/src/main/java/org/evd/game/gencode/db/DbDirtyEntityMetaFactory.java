package org.evd.game.gencode.db;

import org.evd.game.annotation.serialize.DBDirtyEntity;
import org.evd.game.annotation.serialize.DBDirtyTag;
import org.evd.game.annotation.serialize.DBserialize;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

final class DbDirtyEntityMetaFactory {
    private static final String COMMON_DB_DEF_PACKAGE = "org.evd.game.common.dbDef";
    private static final String DATA_DEF_SUFFIX = "Def";
    private static final String DB_ENTITY_PACKAGE_SEGMENT = ".dbEntity";
    private static final String DB_DEF_PACKAGE_SEGMENT = ".dbDef";
    private static final String DB_PACKAGE_SEGMENT = ".db";

    private DbDirtyEntityMetaFactory() {
    }

    static DbDirtyEntityMeta create(TypeElement typeElement, ProcessingEnvironment processingEnv) {
        String sourcePackage = processingEnv.getElementUtils().getPackageOf(typeElement).getQualifiedName().toString();
        String sourceClassName = typeElement.getQualifiedName().toString();
        DbDirtyPackageLayout layout = toPackageLayout(sourcePackage);
        String beanClassName = toGeneratedClassName(typeElement.getSimpleName().toString());
        DBDirtyEntity dbDirtyEntity = typeElement.getAnnotation(DBDirtyEntity.class);
        DBserialize dbType = dbDirtyEntity.value();
        boolean table = dbDirtyEntity.table();
        validateTableConstraint(typeElement, sourcePackage, table);
        String beanPackage = buildPackage(layout.dbRootPackage(), "bean", layout.relativePackage());
        String tablePackage = buildPackage(layout.dbRootPackage(), "table", layout.relativePackage());
        String internalTablePackage = buildPackage(layout.dbRootPackage(), "_table_", layout.relativePackage());
        String registryPackage = resolveRegistryPackage(sourcePackage);
        String legacyFlatPackage = buildPackage(layout.dbRootPackage(), null, layout.relativePackage());
        String legacyBrokenPackage = sourcePackage + DB_PACKAGE_SEGMENT;

        List<DbDirtyFieldMeta> fields = new ArrayList<>();
        for (Element element : typeElement.getEnclosedElements()) {
            if (element instanceof VariableElement variableElement) {
                fields.add(DbDirtyFieldMeta.of(variableElement, processingEnv, beanPackage, dbType, sourceClassName));
            }
        }

        DbDirtyFieldMeta primaryKeyField = validateFields(sourceClassName, table, fields);
        return new DbDirtyEntityMeta(sourcePackage, layout.dbRootPackage(), layout.relativePackage(), registryPackage,
                legacyFlatPackage, legacyBrokenPackage, beanPackage, tablePackage, internalTablePackage,
                beanClassName, beanClassName + "Table", "_" + beanClassName + "Table_",
                dbType, table, fields, primaryKeyField);
    }

    private static DbDirtyFieldMeta validateFields(String className, boolean table, List<DbDirtyFieldMeta> fields) {
        Set<Integer> usedTagValues = new HashSet<>();
        DbDirtyFieldMeta primaryKeyField = null;
        for (DbDirtyFieldMeta field : fields) {
            if (field.tagValue == null) {
                throw new IllegalStateException("所有字段都必须显式标记 @DBDirtyTag: " + className + "." + field.name);
            }
            if (field.tagValue <= 0) {
                throw new IllegalStateException("DBDirtyTag.value 必须 > 0: " + className + "." + field.name);
            }
            if (!usedTagValues.add(field.tagValue)) {
                throw new IllegalStateException("DBDirtyTag.value 不能重复: " + className + "." + field.name + " = " + field.tagValue);
            }
            if (field.primaryKey) {
                if (!field.type.supportPrimaryKey()) {
                    throw new IllegalStateException("primaryKey 只能是 int、long 或 String: " + className + "." + field.name);
                }
                if (primaryKeyField != null) {
                    throw new IllegalStateException("只能声明一个 primaryKey: " + className);
                }
                primaryKeyField = field;
            }
        }
        if (table && primaryKeyField == null) {
            throw new IllegalStateException("table=true 的实体必须至少声明一个 primaryKey: " + className);
        }
        return primaryKeyField;
    }

    private static void validateTableConstraint(TypeElement typeElement, String sourcePackage, boolean table) {
        if (!table) {
            return;
        }
        if (!COMMON_DB_DEF_PACKAGE.equals(sourcePackage)) {
            return;
        }
        throw new IllegalStateException("common:dbdef 模块禁止声明 table=true，请把表定义放到具体 Service dbDef: "
                + typeElement.getQualifiedName());
    }

    static DbDirtyPackageLayout toPackageLayout(String sourcePackage) {
        int index = sourcePackage.indexOf(DB_ENTITY_PACKAGE_SEGMENT);
        if (index < 0) {
            return new DbDirtyPackageLayout(sourcePackage + DB_PACKAGE_SEGMENT, "");
        }
        String prefix = sourcePackage.substring(0, index);
        String suffix = sourcePackage.substring(index + DB_ENTITY_PACKAGE_SEGMENT.length());
        return new DbDirtyPackageLayout(prefix + DB_PACKAGE_SEGMENT, suffix);
    }

    static String resolveRegistryPackage(String sourcePackage) {
        int index = sourcePackage.indexOf(DB_ENTITY_PACKAGE_SEGMENT);
        if (index < 0) {
            index = sourcePackage.indexOf(DB_DEF_PACKAGE_SEGMENT);
        }
        if (index < 0) {
            return sourcePackage + DB_PACKAGE_SEGMENT;
        }
        return sourcePackage.substring(0, index) + DB_PACKAGE_SEGMENT;
    }

    static String buildPackage(String dbRootPackage, String category, String relativePackage) {
        StringBuilder sb = new StringBuilder(dbRootPackage);
        if (category != null && !category.isEmpty()) {
            sb.append(".").append(category);
        }
        if (relativePackage != null && !relativePackage.isEmpty()) {
            if (relativePackage.startsWith(".")) {
                sb.append(relativePackage);
            } else {
                sb.append(".").append(relativePackage);
            }
        }
        return sb.toString();
    }

    static String toGeneratedClassName(String sourceClassName) {
        if (sourceClassName.endsWith(DATA_DEF_SUFFIX)) {
            return sourceClassName.substring(0, sourceClassName.length() - DATA_DEF_SUFFIX.length());
        }
        return sourceClassName;
    }

    static String shortJavaLang(String typeName) {
        return typeName.replace("java.lang.", "");
    }

    static String toSnakeCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }

        StringBuilder sb = new StringBuilder(name.length() + 8);

        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);

            if (Character.isUpperCase(ch)) {
                if (i > 0) {
                    char prev = name.charAt(i - 1);

                    boolean prevIsLowerOrDigit = Character.isLowerCase(prev) || Character.isDigit(prev);
                    boolean prevIsUpper = Character.isUpperCase(prev);

                    boolean nextIsLower = i + 1 < name.length()
                            && Character.isLowerCase(name.charAt(i + 1));

                    if (prevIsLowerOrDigit || (prevIsUpper && nextIsLower)) {
                        sb.append('_');
                    }
                }

                sb.append(Character.toLowerCase(ch));
            } else {
                sb.append(ch);
            }
        }

        return sb.toString();
    }
}

record DbDirtyPackageLayout(String dbRootPackage, String relativePackage) {
}

final class DbDirtyEntityMeta {
    final String sourcePackage;
    final String dbRootPackage;
    final String relativePackage;
    final String registryPackage;
    final String legacyFlatPackage;
    final String legacyBrokenPackage;
    final String beanPackage;
    final String tablePackage;
    final String internalTablePackage;
    final String beanClassName;
    final String tableClassName;
    final String internalTableClassName;
    final DBserialize dbType;
    final boolean table;
    final List<DbDirtyFieldMeta> fields;
    final DbDirtyFieldMeta primaryKeyField;

    DbDirtyEntityMeta(String sourcePackage, String dbRootPackage, String relativePackage, String registryPackage,
                      String legacyFlatPackage, String legacyBrokenPackage, String beanPackage,
                      String tablePackage, String internalTablePackage, String beanClassName,
                      String tableClassName, String internalTableClassName, DBserialize dbType,
                      boolean table, List<DbDirtyFieldMeta> fields, DbDirtyFieldMeta primaryKeyField) {
        this.sourcePackage = sourcePackage;
        this.dbRootPackage = dbRootPackage;
        this.relativePackage = relativePackage;
        this.registryPackage = registryPackage;
        this.legacyFlatPackage = legacyFlatPackage;
        this.legacyBrokenPackage = legacyBrokenPackage;
        this.beanPackage = beanPackage;
        this.tablePackage = tablePackage;
        this.internalTablePackage = internalTablePackage;
        this.beanClassName = beanClassName;
        this.tableClassName = tableClassName;
        this.internalTableClassName = internalTableClassName;
        this.dbType = dbType;
        this.table = table;
        this.fields = fields;
        this.primaryKeyField = primaryKeyField;
    }

    boolean usesList() {
        return fields.stream().anyMatch(field -> field.type.containsKind(DbDirtyTypeKind.LIST));
    }

    boolean usesMap() {
        return fields.stream().anyMatch(field -> field.type.containsKind(DbDirtyTypeKind.MAP));
    }

    boolean usesSet() {
        return fields.stream().anyMatch(field -> field.type.containsKind(DbDirtyTypeKind.SET));
    }

    boolean hasTaggedField() {
        return fields.stream().anyMatch(field -> field.tagValue != null);
    }

    List<DbDirtyFieldMeta> tableFields() {
        if (primaryKeyField == null) {
            return fields;
        }
        List<DbDirtyFieldMeta> ordered = new ArrayList<>(fields.size());
        ordered.add(primaryKeyField);
        for (DbDirtyFieldMeta field : fields) {
            if (field != primaryKeyField) {
                ordered.add(field);
            }
        }
        return ordered;
    }

    String tableName() {
        return DbDirtyEntityMetaFactory.toSnakeCase(beanClassName);
    }

    String tableTypeName() {
        return tablePackage + "." + tableClassName;
    }

    String internalTableTypeName() {
        return internalTablePackage + "." + internalTableClassName;
    }
}

final class DbDirtyFieldMeta {
    final String name;
    final String methodSuffix;
    final String columnName;
    final Integer tagValue;
    final boolean primaryKey;
    final DbDirtyTypeMeta type;

    DbDirtyFieldMeta(String name, String methodSuffix, Integer tagValue, boolean primaryKey, DbDirtyTypeMeta type) {
        this.name = name;
        this.methodSuffix = methodSuffix;
        this.columnName = DbDirtyEntityMetaFactory.toSnakeCase(name);
        this.tagValue = tagValue;
        this.primaryKey = primaryKey;
        this.type = type;
    }

    static DbDirtyFieldMeta of(VariableElement field, ProcessingEnvironment processingEnv, String currentBeanPackage,
                               DBserialize ownerSerialize, String ownerClassName) {
        DBDirtyTag dbDirtyTag = field.getAnnotation(DBDirtyTag.class);
        Integer tagValue = dbDirtyTag == null ? null : dbDirtyTag.value();
        boolean primaryKey = dbDirtyTag != null && dbDirtyTag.primaryKey();
        String name = field.getSimpleName().toString();
        return new DbDirtyFieldMeta(name, upperFirst(name), tagValue, primaryKey,
                DbDirtyTypeMeta.of(field.asType(), processingEnv, currentBeanPackage, ownerSerialize, ownerClassName, name));
    }

    private static String upperFirst(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}

final class DbDirtyTypeMeta {
    final DbDirtyTypeKind kind;
    final String fieldType;
    final String getterType;
    final boolean primaryKeySupported;
    final DbDirtyTypeMeta elementType;
    final DbDirtyTypeMeta keyType;
    final DbDirtyTypeMeta valueType;
    final String qualifiedEntityType;

    DbDirtyTypeMeta(DbDirtyTypeKind kind, String fieldType, String getterType, boolean primaryKeySupported,
                    DbDirtyTypeMeta elementType, DbDirtyTypeMeta keyType, DbDirtyTypeMeta valueType,
                    String qualifiedEntityType) {
        this.kind = kind;
        this.fieldType = fieldType;
        this.getterType = getterType;
        this.primaryKeySupported = primaryKeySupported;
        this.elementType = elementType;
        this.keyType = keyType;
        this.valueType = valueType;
        this.qualifiedEntityType = qualifiedEntityType;
    }

    static DbDirtyTypeMeta of(TypeMirror typeMirror, ProcessingEnvironment processingEnv, String currentBeanPackage,
                              DBserialize ownerSerialize, String ownerClassName, String fieldName) {
        if (typeMirror.getKind().isPrimitive()) {
            String typeName = typeMirror.toString();
            return new DbDirtyTypeMeta(DbDirtyTypeKind.PRIMITIVE, typeName, typeName,
                    "int".equals(typeName) || "long".equals(typeName),
                    null, null, null, null);
        }

        if (typeMirror instanceof ArrayType arrayType) {
            if (arrayType.getComponentType().getKind() == javax.lang.model.type.TypeKind.BYTE) {
                return new DbDirtyTypeMeta(DbDirtyTypeKind.OTHER, "byte[]", "byte[]",
                        false, null, null, null, null);
            }
            throw unsupportedCustomType(ownerClassName, fieldName, typeMirror.toString());
        }

        if (typeMirror instanceof DeclaredType declaredType) {
            TypeElement typeElement = (TypeElement) declaredType.asElement();
            String qualifiedName = typeElement.getQualifiedName().toString();
            if (qualifiedName.equals(String.class.getCanonicalName())) {
                return new DbDirtyTypeMeta(DbDirtyTypeKind.STRING, "String", "String", true, null, null, null, null);
            }
            if (qualifiedName.equals(List.class.getCanonicalName())) {
                DbDirtyTypeMeta elementType = of(declaredType.getTypeArguments().get(0), processingEnv, currentBeanPackage,
                        ownerSerialize, ownerClassName, fieldName);
                String genericType = elementType.fieldType;
                return new DbDirtyTypeMeta(DbDirtyTypeKind.LIST,
                        "XArrayList<" + genericType + ">",
                        "java.util.List<" + genericType + ">",
                        false,
                        elementType, null, null, null);
            }
            if (qualifiedName.equals(Set.class.getCanonicalName())) {
                DbDirtyTypeMeta elementType = of(declaredType.getTypeArguments().get(0), processingEnv, currentBeanPackage,
                        ownerSerialize, ownerClassName, fieldName);
                String genericType = elementType.fieldType;
                return new DbDirtyTypeMeta(DbDirtyTypeKind.SET,
                        "XHashSet<" + genericType + ">",
                        "java.util.Set<" + genericType + ">",
                        false,
                        elementType, null, null, null);
            }
            if (qualifiedName.equals(Map.class.getCanonicalName())) {
                DbDirtyTypeMeta keyType = of(declaredType.getTypeArguments().get(0), processingEnv, currentBeanPackage,
                        ownerSerialize, ownerClassName, fieldName);
                DbDirtyTypeMeta valueType = of(declaredType.getTypeArguments().get(1), processingEnv, currentBeanPackage,
                        ownerSerialize, ownerClassName, fieldName);
                return new DbDirtyTypeMeta(DbDirtyTypeKind.MAP,
                        "XHashMap<" + keyType.fieldType + ", " + valueType.fieldType + ">",
                        "java.util.Map<" + keyType.fieldType + ", " + valueType.fieldType + ">",
                        false,
                        null, keyType, valueType, null);
            }
            DBDirtyEntity childEntity = typeElement.getAnnotation(DBDirtyEntity.class);
            if (childEntity != null) {
                if (childEntity.value() != ownerSerialize) {
                    throw new IllegalStateException("父子 DBserialize 必须一致: " + ownerClassName + "." + fieldName
                            + " -> " + typeElement.getQualifiedName() + "，父=" + ownerSerialize + "，子=" + childEntity.value());
                }
                String qualifiedEntityType = renderQualifiedDbEntityType(typeElement, processingEnv);
                String typeName = renderDbEntityType(typeElement, processingEnv, currentBeanPackage);
                return new DbDirtyTypeMeta(DbDirtyTypeKind.ENTITY, typeName, typeName, false,
                        null, null, null, qualifiedEntityType);
            }
            if (!isSupportedSimpleDeclaredType(qualifiedName)) {
                throw unsupportedCustomType(ownerClassName, fieldName, typeElement.getQualifiedName().toString());
            }
            String typeName = renderDeclaredType(declaredType, processingEnv, currentBeanPackage,
                    ownerSerialize, ownerClassName, fieldName);
            return new DbDirtyTypeMeta(DbDirtyTypeKind.OTHER, typeName, typeName,
                    "Integer".equals(typeName) || "Long".equals(typeName) || "String".equals(typeName),
                    null, null, null, null);
        }

        throw unsupportedCustomType(ownerClassName, fieldName, typeMirror.toString());
    }

    boolean containsKind(DbDirtyTypeKind targetKind) {
        if (kind == targetKind) {
            return true;
        }
        return (elementType != null && elementType.containsKind(targetKind))
                || (keyType != null && keyType.containsKind(targetKind))
                || (valueType != null && valueType.containsKind(targetKind));
    }

    boolean supportPrimaryKey() {
        return primaryKeySupported;
    }

    boolean isMysqlScalar() {
        if (kind == DbDirtyTypeKind.STRING || kind == DbDirtyTypeKind.PRIMITIVE) {
            return true;
        }
        if (kind != DbDirtyTypeKind.OTHER) {
            return false;
        }
        return switch (fieldType) {
            case "Byte", "Short", "Integer", "Long", "Float", "Double", "Boolean", "Character", "String" -> true;
            default -> false;
        };
    }

    String boxedType() {
        return switch (fieldType) {
            case "byte" -> "Byte";
            case "short" -> "Short";
            case "int" -> "Integer";
            case "long" -> "Long";
            case "float" -> "Float";
            case "double" -> "Double";
            case "boolean" -> "Boolean";
            case "char" -> "Character";
            default -> fieldType;
        };
    }

    void collectQualifiedEntityTypes(Set<String> collector) {
        if (kind == DbDirtyTypeKind.ENTITY && qualifiedEntityType != null) {
            collector.add(qualifiedEntityType);
        }
        if (elementType != null) {
            elementType.collectQualifiedEntityTypes(collector);
        }
        if (keyType != null) {
            keyType.collectQualifiedEntityTypes(collector);
        }
        if (valueType != null) {
            valueType.collectQualifiedEntityTypes(collector);
        }
    }

    private static String renderDbEntityType(TypeElement typeElement, ProcessingEnvironment processingEnv, String currentBeanPackage) {
        String qualifiedTypeName = renderQualifiedDbEntityType(typeElement, processingEnv);
        int lastDot = qualifiedTypeName.lastIndexOf('.');
        String targetPackage = lastDot < 0 ? "" : qualifiedTypeName.substring(0, lastDot);
        String className = lastDot < 0 ? qualifiedTypeName : qualifiedTypeName.substring(lastDot + 1);
        if (targetPackage.equals(currentBeanPackage)) {
            return className;
        }
        return qualifiedTypeName;
    }

    private static String renderQualifiedDbEntityType(TypeElement typeElement, ProcessingEnvironment processingEnv) {
        String sourcePackage = processingEnv.getElementUtils().getPackageOf(typeElement).getQualifiedName().toString();
        DbDirtyPackageLayout layout = DbDirtyEntityMetaFactory.toPackageLayout(sourcePackage);
        String targetPackage = DbDirtyEntityMetaFactory.buildPackage(layout.dbRootPackage(), "bean", layout.relativePackage());
        String className = DbDirtyEntityMetaFactory.toGeneratedClassName(typeElement.getSimpleName().toString());
        return targetPackage + "." + className;
    }

    private static String renderDeclaredType(DeclaredType declaredType, ProcessingEnvironment processingEnv,
                                             String currentBeanPackage, DBserialize ownerSerialize,
                                             String ownerClassName, String fieldName) {
        TypeElement typeElement = (TypeElement) declaredType.asElement();
        String qualifiedName = typeElement.getQualifiedName().toString();
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
        if (typeArguments.isEmpty()) {
            return DbDirtyEntityMetaFactory.shortJavaLang(qualifiedName);
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (TypeMirror typeArgument : typeArguments) {
            joiner.add(of(typeArgument, processingEnv, currentBeanPackage,
                    ownerSerialize, ownerClassName, fieldName).fieldType);
        }
        return DbDirtyEntityMetaFactory.shortJavaLang(qualifiedName) + "<" + joiner + ">";
    }

    private static boolean isSupportedSimpleDeclaredType(String qualifiedName) {
        return switch (qualifiedName) {
            case "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long",
                    "java.lang.Float", "java.lang.Double", "java.lang.Boolean", "java.lang.Character" -> true;
            default -> false;
        };
    }

    private static IllegalStateException unsupportedCustomType(String ownerClassName, String fieldName, String typeName) {
        return new IllegalStateException("自定义字段类型必须显式标记 @DBDirtyEntity: "
                + ownerClassName + "." + fieldName + " -> " + typeName);
    }
}

enum DbDirtyTypeKind {
    PRIMITIVE,
    STRING,
    ENTITY,
    LIST,
    SET,
    MAP,
    OTHER
}
