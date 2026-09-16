package org.evd.game.gencode.rpc;

import com.google.auto.service.AutoService;
import org.evd.game.annotation.actor.Actor;
import org.evd.game.annotation.actor.Rpc;
import org.evd.game.annotation.actor.RpcHandler;
import org.evd.game.gencode.ProcessorBase;
import org.evd.game.gencode.struct.MethodStruct;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@AutoService(Processor.class)
public class RpcProcessor extends ProcessorBase {
    private RpcSupport support;
    private RpcProxyFileGenerator proxyFileGenerator;

    @Override
    protected Set<String> supportAnnotation() {
        return Set.of(
                Rpc.class.getCanonicalName(),
                RpcHandler.class.getCanonicalName(),
                Actor.class.getCanonicalName());
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
            for (TypeElement ownerType : support.resolveServiceOwnersForCleanup(roundEnv)) {
                proxyFileGenerator.cleanupStaleServiceProxies(ownerType, Set.of());
            }
            return;
        }

        // RpcProcessor 是 aggregating：先按本轮完整 RPC 集合生成，再清理旧 Proxy 差集。
        Map<String, List<MethodStruct<Rpc>>> classMap = context.classMap;
        Set<String> expectedProxyFiles = new HashSet<>(classMap.size());
        for (List<MethodStruct<Rpc>> classMethods : classMap.values()) {
            expectedProxyFiles.add(proxyFileGenerator.generatedProxyFileName(
                    classMethods.getFirst().getTypeElement()));
            proxyFileGenerator.generate(classMethods.getFirst().getTypeElement(), classMethods);
        }
        proxyFileGenerator.cleanupStaleServiceProxies(context.ownerType, expectedProxyFiles);
    }
}
