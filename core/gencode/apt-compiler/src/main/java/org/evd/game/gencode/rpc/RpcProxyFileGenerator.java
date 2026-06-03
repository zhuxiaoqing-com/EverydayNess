package org.evd.game.gencode.rpc;

import org.evd.game.annotation.Rpc;
import org.evd.game.gencode.GenConst;
import org.evd.game.gencode.struct.MethodStruct;

import javax.lang.model.element.TypeElement;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

final class RpcProxyFileGenerator {
    private static final String COMMON_PROXY_PACKAGE = "org.evd.game.common.proxy";

    private final RpcSupport support;

    RpcProxyFileGenerator(RpcSupport support) {
        this.support = support;
    }

    void generate(TypeElement typeElement, List<MethodStruct<Rpc>> methods) {
        try {
            Path proxyDir = resolveProxyDir();
            Files.createDirectories(proxyDir);
            cleanupOldProxyFiles(proxyDir, typeElement);

            String generatedClassName = typeElement.getSimpleName() + "Proxy.java";
            String content = support.renderTemplate(RpcSupport.TEMPLATE_RPC_PROXY, support.buildProxyRootMap(methods));
            writeIfChanged(proxyDir.resolve(generatedClassName), content);
            writeManifest(resolveManifestFile(proxyDir, typeElement), List.of(generatedClassName));
        } catch (Exception e) {
            throw new RuntimeException("生成Rpc跨模块代理失败", e);
        }
    }

    private void cleanupOldProxyFiles(Path proxyDir, TypeElement ownerType) throws IOException {
        Path manifestFile = resolveManifestFile(proxyDir, ownerType);
        if (!Files.exists(manifestFile)) {
            return;
        }
        for (String fileName : Files.readAllLines(manifestFile, StandardCharsets.UTF_8)) {
            if (!fileName.isBlank()) {
                Files.deleteIfExists(proxyDir.resolve(fileName.trim()));
            }
        }
        Files.deleteIfExists(manifestFile);
    }

    private void writeManifest(Path manifestFile, List<String> generatedFiles) throws IOException {
        Files.write(manifestFile, generatedFiles, StandardCharsets.UTF_8);
    }

    private Path resolveManifestFile(Path proxyDir, TypeElement ownerType) {
        String ownerFullClassName = ownerType.getQualifiedName().toString().replaceAll("[^A-Za-z0-9]", "_");
        return proxyDir.resolve("_rpc_proxy_manifest_" + ownerFullClassName + ".txt");
    }

    private void writeIfChanged(Path filePath, String content) throws IOException {
        if (Files.exists(filePath)) {
            String oldContent = Files.readString(filePath, StandardCharsets.UTF_8);
            if (oldContent.equals(content)) {
                return;
            }
        }
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
    }

    private Path resolveProxyDir() {
        return Paths.get(GenConst.ROOT_PROJECT_PATH,
                "common", "src", "gen", "java",
                COMMON_PROXY_PACKAGE.replace('.', File.separatorChar));
    }
}
