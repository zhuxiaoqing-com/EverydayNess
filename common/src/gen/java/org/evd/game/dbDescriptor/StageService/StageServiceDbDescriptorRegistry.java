package org.evd.game.dbDescriptor.StageService;

import org.evd.game.dbDescriptor.DbEntityDescriptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generated registry for StageService DB descriptors.
 */
public final class StageServiceDbDescriptorRegistry {
    public static final List<DbEntityDescriptor> ALL = List.of(
            DBItemDataDefDescriptor.INSTANCE,
            DBItemDataJSONDefDescriptor.INSTANCE,
            DBItemDataMysqlDefDescriptor.INSTANCE,
            DBPlayerDataDefDescriptor.INSTANCE,
            DBPlayerDataJSONDefDescriptor.INSTANCE,
            DBPlayerDataMysqlDefDescriptor.INSTANCE
    );

    public static final Map<String, DbEntityDescriptor> BY_ENTITY_NAME = buildByEntityName();

    private StageServiceDbDescriptorRegistry() {
    }

    public static DbEntityDescriptor get(String entityName) {
        return BY_ENTITY_NAME.get(entityName);
    }

    private static Map<String, DbEntityDescriptor> buildByEntityName() {
        Map<String, DbEntityDescriptor> descriptors = new LinkedHashMap<>();
        for (DbEntityDescriptor descriptor : ALL) {
            descriptors.put(descriptor.getEntityName(), descriptor);
        }
        return Map.copyOf(descriptors);
    }
}
