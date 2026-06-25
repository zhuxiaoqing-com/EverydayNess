package org.evd.game.gencode.rpc;

import com.google.auto.service.AutoService;
import org.evd.game.annotation.Rpc;
import org.evd.game.gencode.ProcessorBase;
import org.evd.game.gencode.struct.MethodStruct;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.Writer;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@AutoService(Processor.class)
public class RpcImplProcessor extends ProcessorBase {
    private RpcSupport support;

    @Override
    protected Set<String> supportAnnotation() {
        return Collections.singleton(Rpc.class.getCanonicalName());
    }

    @Override
    protected void init() {
        support = new RpcSupport(processingEnv);
    }

    @Override
    protected void gen(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        println("");
        println("开始执行RpcImpl Processor");

        RpcGenerationContext context = support.buildContext(roundEnv);
        if (context == null) {
            return;
        }

        String ownerFullClassName = context.ownerType.getQualifiedName().toString();
        Map<String, Object> rootMap = support.buildRootMap(context.ownerMethods, ownerFullClassName);
        String implFullClassName = ownerFullClassName + "Impl";
        try {
            String content = support.renderTemplate(RpcSupport.TEMPLATE_RPC_IMP, rootMap);
            JavaFileObject sourceFile = filer.createSourceFile(implFullClassName, collectOriginatingElements(context));
            try (Writer writer = sourceFile.openWriter()) {
                writer.write(content);
            }
            println("generate success [" + implFullClassName.substring(implFullClassName.lastIndexOf('.') + 1) + ".java]");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Element[] collectOriginatingElements(RpcGenerationContext context) {
        LinkedHashSet<Element> originatingElements = new LinkedHashSet<>();
        originatingElements.add(context.ownerType);
        for (MethodStruct<Rpc> method : context.structList) {
            originatingElements.add(method.getExecutableElement());
        }
        return originatingElements.toArray(Element[]::new);
    }
}
