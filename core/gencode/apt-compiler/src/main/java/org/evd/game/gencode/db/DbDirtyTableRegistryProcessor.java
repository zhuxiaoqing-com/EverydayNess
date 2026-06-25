package org.evd.game.gencode.db;

import com.google.auto.service.AutoService;
import org.evd.game.annotation.DBDirtyEntity;
import org.evd.game.gencode.ProcessorBase;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Collections;
import java.util.Set;

@AutoService(Processor.class)
public class DbDirtyTableRegistryProcessor extends ProcessorBase {
    private DbDirtyTableRegistryGenerator generator;

    @Override
    protected Set<String> supportAnnotation() {
        return Collections.singleton(DBDirtyEntity.class.getCanonicalName());
    }

    @Override
    protected void init() {
        generator = new DbDirtyTableRegistryGenerator(processingEnv, filer);
    }

    @Override
    protected void gen(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        println("");
        println("开始执行DbDirtyTableRegistry Processor");

        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(DBDirtyEntity.class);
        if (elements != null) {
            for (Element element : elements) {
                if (element instanceof TypeElement typeElement) {
                    generator.collect(typeElement);
                }
            }
        }

        if (!roundEnv.processingOver()) {
            return;
        }
        generator.generateAll();
    }
}
