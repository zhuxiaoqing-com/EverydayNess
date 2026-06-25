package org.evd.game.gencode;

import com.google.auto.service.AutoService;
import org.evd.game.annotation.Rpc;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Collections;
import java.util.Set;

@AutoService(Processor.class)
public class ServiceProcessor extends ProcessorBase {

    @Override
    protected Set<String> supportAnnotation() {
        return Collections.singleton(Rpc.class.getCanonicalName());
    }

    @Override
    protected void init() {

    }

    @Override
    protected void gen(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        messager.printMessage(Diagnostic.Kind.NOTE, "");
        messager.printMessage(Diagnostic.Kind.NOTE, "开始执行Service Processor");
    }
}
