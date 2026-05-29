package org.evd.game.dbDescriptor;

/**
 * 单个字段的静态描述。
 */
public final class DbFieldDescriptor {
    private final int tag;
    private final String name;
    private final String javaTypeName;
    private final boolean primaryKey;
    private final String comment;
    private final String nestedDescriptorName;

    public DbFieldDescriptor(int tag, String name, String javaTypeName, boolean primaryKey, String comment, String nestedDescriptorName) {
        this.tag = tag;
        this.name = name;
        this.javaTypeName = javaTypeName;
        this.primaryKey = primaryKey;
        this.comment = comment;
        this.nestedDescriptorName = nestedDescriptorName;
    }

    public int getTag() {
        return tag;
    }

    public String getName() {
        return name;
    }

    public String getJavaTypeName() {
        return javaTypeName;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public String getComment() {
        return comment;
    }

    public String getNestedDescriptorName() {
        return nestedDescriptorName;
    }
}
