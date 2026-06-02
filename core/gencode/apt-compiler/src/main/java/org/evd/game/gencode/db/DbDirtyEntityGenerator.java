package org.evd.game.gencode.db;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

final class DbDirtyEntityGenerator {
    private final ProcessingEnvironment processingEnv;
    private final DbDirtyGeneratedJavaFileWriter fileWriter;
    private final DbDirtyBeanRenderer beanRenderer = new DbDirtyBeanRenderer();
    private final DbDirtyTableFacadeRenderer tableFacadeRenderer = new DbDirtyTableFacadeRenderer();
    private final DbDirtyStorageTableRenderer storageTableRenderer = new DbDirtyStorageTableRenderer();

    DbDirtyEntityGenerator(ProcessingEnvironment processingEnv, Filer filer) {
        this.processingEnv = processingEnv;
        this.fileWriter = new DbDirtyGeneratedJavaFileWriter(filer);
    }

    void generate(TypeElement typeElement) {
        DbDirtyEntityMeta entity = DbDirtyEntityMetaFactory.create(typeElement, processingEnv);
        fileWriter.deleteLegacyFiles(entity);
        fileWriter.writeJavaFile(entity.beanPackage, entity.beanClassName, beanRenderer.render(entity));
        if (!entity.table) {
            return;
        }
        fileWriter.writeJavaFile(entity.tablePackage, entity.tableClassName, tableFacadeRenderer.render(entity));
        fileWriter.writeJavaFile(entity.internalTablePackage, entity.internalTableClassName, storageTableRenderer.render(entity));
    }
}
