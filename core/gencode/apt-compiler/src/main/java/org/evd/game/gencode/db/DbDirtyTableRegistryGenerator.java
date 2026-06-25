package org.evd.game.gencode.db;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class DbDirtyTableRegistryGenerator {
    private static final String REGISTRY_CLASS_NAME = "DbTableRegistry";

    private final ProcessingEnvironment processingEnv;
    private final DbDirtyGeneratedJavaFileWriter fileWriter;
    private final DbDirtyTableRegistryRenderer renderer = new DbDirtyTableRegistryRenderer();
    private final Map<String, RegistryGroup> registryGroups = new LinkedHashMap<>();
    private boolean generated;

    DbDirtyTableRegistryGenerator(ProcessingEnvironment processingEnv, Filer filer) {
        this.processingEnv = processingEnv;
        this.fileWriter = new DbDirtyGeneratedJavaFileWriter(filer);
    }

    void collect(TypeElement typeElement) {
        DbDirtyEntityMeta entity = DbDirtyEntityMetaFactory.create(typeElement, processingEnv);
        if (!entity.table) {
            return;
        }
        RegistryGroup group = registryGroups.computeIfAbsent(entity.registryPackage, RegistryGroup::new);
        group.add(typeElement, entity);
    }

    void generateAll() {
        if (generated) {
            return;
        }
        generated = true;
        for (RegistryGroup group : registryGroups.values()) {
            List<DbDirtyEntityMeta> entities = group.sortedEntities();
            fileWriter.writeJavaFile(group.packageName, REGISTRY_CLASS_NAME,
                    renderer.render(group.packageName, entities), group.originatingElements());
        }
    }

    private static final class RegistryGroup {
        private final String packageName;
        private final Map<String, DbDirtyEntityMeta> entityMap = new LinkedHashMap<>();
        private final LinkedHashSet<Element> originatingElements = new LinkedHashSet<>();

        private RegistryGroup(String packageName) {
            this.packageName = packageName;
        }

        private void add(TypeElement typeElement, DbDirtyEntityMeta entity) {
            entityMap.putIfAbsent(entity.internalTableTypeName(), entity);
            originatingElements.add(typeElement);
        }

        private List<DbDirtyEntityMeta> sortedEntities() {
            ArrayList<DbDirtyEntityMeta> entities = new ArrayList<>(entityMap.values());
            entities.sort(Comparator.comparing(DbDirtyEntityMeta::internalTableTypeName));
            return entities;
        }

        private Element[] originatingElements() {
            return originatingElements.toArray(Element[]::new);
        }
    }
}
