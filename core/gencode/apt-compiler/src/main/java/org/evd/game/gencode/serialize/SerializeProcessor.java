package org.evd.game.gencode.serialize;

import com.google.auto.service.AutoService;
import org.evd.game.annotation.SerializeClass;
import org.evd.game.gencode.ProcessorBase;
import org.evd.game.gencode.struct.ClassStruct;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@AutoService(Processor.class)
public class SerializeProcessor extends ProcessorBase {
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
        println("开始执行Serialize Processor");

        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(SerializeClass.class);
        if (elements == null || elements.isEmpty()) {
            return;
        }

        Map<String, List<ClassStruct>> registerGroups = new LinkedHashMap<>();
        for (Element element : elements) {
            ClassStruct struct = new ClassStruct(element, processingEnv);
            String packageName = SerializeSupport.resolveRegisterPackage(struct);
            registerGroups.computeIfAbsent(packageName, key -> new ArrayList<>()).add(struct);
        }

        for (Map.Entry<String, List<ClassStruct>> entry : registerGroups.entrySet()) {
            genSerializerRegister(entry.getKey(), entry.getValue());
        }
    }

    private void genSerializerRegister(String packageName, List<ClassStruct> structList) {
        String fullClassName = packageName + "." + SerializeSupport.REGISTER_CLASS;
        if (!generatedClasses.add(fullClassName)) {
            return;
        }

        try {
            String content = SerializeSupport.renderTemplate(
                    SerializeSupport.TEMPLATE_SERIALIZE_REGISTER,
                    SerializeSupport.buildRegisterModel(packageName, structList)
            );
            JavaFileObject sourceFile = filer.createSourceFile(fullClassName, collectOriginatingElements(structList));
            try (Writer writer = sourceFile.openWriter()) {
                writer.write(content);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Element[] collectOriginatingElements(List<ClassStruct> structList) {
        LinkedHashSet<Element> originatingElements = new LinkedHashSet<>();
        for (ClassStruct struct : structList) {
            originatingElements.add(struct.getElement());
        }
        return originatingElements.toArray(Element[]::new);
    }
}
