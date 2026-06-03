package org.evd.game.gencode.serialize;

import com.google.auto.service.AutoService;
import org.evd.game.annotation.SerializeClass;
import org.evd.game.base.ISerializable;
import org.evd.game.gencode.ProcessorBase;
import org.evd.game.gencode.struct.ClassStruct;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.Writer;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@AutoService(Processor.class)
public class SerializeIoProcessor extends ProcessorBase {
    private final Set<String> generatedClasses = new LinkedHashSet<>();

    @Override
    protected Set<String> supportAnnotation() {
        return Collections.singleton(SerializeClass.class.getCanonicalName());
    }

    @Override
    protected void init() {
    }

    @Override
    protected void gen(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        println("");
        println("开始执行SerializeIo Processor");

        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(SerializeClass.class);
        if (elements == null || elements.isEmpty()) {
            return;
        }

        for (Element element : elements) {
            ClassStruct struct = new ClassStruct(element, processingEnv);
            genIoSerializer(struct);
        }
    }

    private void genIoSerializer(ClassStruct clazz) {
        if (clazz.isEnum()) {
            return;
        }
        if (clazz.isCustomizedSerialize() && !clazz.isAssignableFrom(ISerializable.class)) {
            throw new IllegalStateException("自定义序列化类必须实现 ISerializable: " + clazz.getFullClassName());
        }

        String fullClassName = clazz.getPackageName() + "." + clazz.getClassName() + SerializeSupport.CLASS_SUFFIX;
        if (!generatedClasses.add(fullClassName)) {
            return;
        }

        try {
            String content = SerializeSupport.renderTemplate(
                    SerializeSupport.TEMPLATE_SERIALIZE_IO,
                    SerializeSupport.buildIoModel(clazz)
            );
            JavaFileObject sourceFile = filer.createSourceFile(fullClassName, clazz.getElement());
            try (Writer writer = sourceFile.openWriter()) {
                writer.write(content);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
