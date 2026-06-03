package org.evd.game.gencode.rpc;

import com.google.auto.service.AutoService;
import org.evd.game.annotation.Rpc;
import org.evd.game.gencode.ProcessorBase;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import java.util.Collections;
import java.util.Set;

@AutoService(Processor.class)
public class RpcProcessor extends ProcessorBase {
    private RpcSupport support;
    private RpcProxyFileGenerator proxyFileGenerator;

    @Override
    protected Set<String> supportAnnotation() {
        return Collections.singleton(Rpc.class.getCanonicalName());
    }

    @Override
    protected void init() {
        support = new RpcSupport(processingEnv);
        proxyFileGenerator = new RpcProxyFileGenerator(support);
    }

    @Override
    protected void gen(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        println("");
        println("开始执行Rpc Processor");

        RpcGenerationContext context = support.buildContext(roundEnv);
        if (context == null) {
            return;
        }
        proxyFileGenerator.generate(context);
    }
}
