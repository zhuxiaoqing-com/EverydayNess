package org.evd.game.gencode.rpc;

import com.google.auto.service.AutoService;
import org.evd.game.annotation.Rpc;
import org.evd.game.gencode.ProcessorBase;
import org.evd.game.gencode.struct.MethodStruct;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

        List<MethodStruct<Rpc>> methods = support.buildRpcMethodStructs(roundEnv);
        if (methods.isEmpty()) {
            return;
        }
        Map<String, List<MethodStruct<Rpc>>> classMap = support.groupRpcMethodsByClass(methods);
        for (List<MethodStruct<Rpc>> classMethods : classMap.values()) {
            proxyFileGenerator.generate(classMethods.getFirst().getTypeElement(), classMethods);
        }
    }
}
