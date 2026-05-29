package org.evd.game.dbDescriptor;

import org.evd.game.annotation.DBserialize;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个 DB 实体的静态描述。
 */
public final class DbEntityDescriptor {
    private final String serviceName;
    private final String entityName;
    private final String sourceTypeName;
    private final String tableName;
    private final DBserialize serializeType;
    private final boolean tableEntity;
    private final List<DbFieldDescriptor> fields;
    private final Map<DbDescriptorOp, DbRequestShape> requestShapes;
    private final Map<DbDescriptorOp, DbReturnShape> returnShapes;

    private DbEntityDescriptor(
            String serviceName,
            String entityName,
            String sourceTypeName,
            String tableName,
            DBserialize serializeType,
            boolean tableEntity,
            List<DbFieldDescriptor> fields,
            Map<DbDescriptorOp, DbRequestShape> requestShapes,
            Map<DbDescriptorOp, DbReturnShape> returnShapes) {
        this.serviceName = serviceName;
        this.entityName = entityName;
        this.sourceTypeName = sourceTypeName;
        this.tableName = tableName;
        this.serializeType = serializeType;
        this.tableEntity = tableEntity;
        this.fields = List.copyOf(fields);
        this.requestShapes = Collections.unmodifiableMap(new EnumMap<>(requestShapes));
        this.returnShapes = Collections.unmodifiableMap(new EnumMap<>(returnShapes));
    }

    public static Builder builder(
            String serviceName,
            String entityName,
            String sourceTypeName,
            String tableName,
            DBserialize serializeType,
            boolean tableEntity) {
        return new Builder(serviceName, entityName, sourceTypeName, tableName, serializeType, tableEntity);
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getSourceTypeName() {
        return sourceTypeName;
    }

    public String getTableName() {
        return tableName;
    }

    public DBserialize getSerializeType() {
        return serializeType;
    }

    public boolean isTableEntity() {
        return tableEntity;
    }

    public List<DbFieldDescriptor> getFields() {
        return fields;
    }

    public DbFieldDescriptor getPrimaryKeyField() {
        for (DbFieldDescriptor field : fields) {
            if (field.isPrimaryKey()) {
                return field;
            }
        }
        return null;
    }

    public DbRequestShape getRequestShape(DbDescriptorOp op) {
        return requestShapes.getOrDefault(op, DbRequestShape.NONE);
    }

    public DbReturnShape getReturnShape(DbDescriptorOp op) {
        return returnShapes.getOrDefault(op, DbReturnShape.NONE);
    }

    public Map<DbDescriptorOp, DbRequestShape> getRequestShapes() {
        return requestShapes;
    }

    public Map<DbDescriptorOp, DbReturnShape> getReturnShapes() {
        return returnShapes;
    }

    public static final class Builder {
        private final String serviceName;
        private final String entityName;
        private final String sourceTypeName;
        private final String tableName;
        private final DBserialize serializeType;
        private final boolean tableEntity;
        private final List<DbFieldDescriptor> fields = new java.util.ArrayList<>();
        private final Map<DbDescriptorOp, DbRequestShape> requestShapes = new EnumMap<>(DbDescriptorOp.class);
        private final Map<DbDescriptorOp, DbReturnShape> returnShapes = new EnumMap<>(DbDescriptorOp.class);

        private Builder(
                String serviceName,
                String entityName,
                String sourceTypeName,
                String tableName,
                DBserialize serializeType,
                boolean tableEntity) {
            this.serviceName = serviceName;
            this.entityName = entityName;
            this.sourceTypeName = sourceTypeName;
            this.tableName = tableName;
            this.serializeType = serializeType;
            this.tableEntity = tableEntity;
        }

        public Builder addField(int tag, String name, String javaTypeName, boolean primaryKey, String comment, String nestedDescriptorName) {
            fields.add(new DbFieldDescriptor(tag, name, javaTypeName, primaryKey, comment, nestedDescriptorName));
            return this;
        }

        public Builder operation(DbDescriptorOp op, DbRequestShape requestShape, DbReturnShape returnShape) {
            requestShapes.put(op, requestShape);
            returnShapes.put(op, returnShape);
            return this;
        }

        public DbEntityDescriptor build() {
            return new DbEntityDescriptor(
                    serviceName,
                    entityName,
                    sourceTypeName,
                    tableName,
                    serializeType,
                    tableEntity,
                    fields,
                    requestShapes,
                    returnShapes);
        }
    }
}
