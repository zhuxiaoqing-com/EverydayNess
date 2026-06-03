package org.evd.game.gencode.rpc;

import org.evd.game.annotation.Rpc;
import org.evd.game.gencode.struct.MethodStruct;

import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class RpcProxyFileGenerator {
    private static final String COMMON_PROXY_PACKAGE = "org.evd.game.common.proxy";

    private final RpcSupport support;

    RpcProxyFileGenerator(RpcSupport support) {
        this.support = support;
    }

    void generate(RpcGenerationContext context) {
        try {
            Path proxyDir = resolveProxyDir();
            Files.createDirectories(proxyDir);
            cleanupOldProxyFiles(proxyDir, context.ownerType);

            List<String> generatedFiles = new ArrayList<>();
            for (Map.Entry<String, List<MethodStruct<Rpc>>> entry : context.classMap.entrySet()) {
                String classFullName = entry.getKey();
                Map<String, Object> rootMap = support.buildRootMap(entry.getValue(), context.ownerType, classFullName);
                String className = classFullName.substring(classFullName.lastIndexOf('.') + 1);
                String javaFileName = className + "Proxy.java";
                String content = support.renderTemplate(RpcSupport.TEMPLATE_RPC_PROXY, rootMap);
                Files.writeString(proxyDir.resolve(javaFileName), content, StandardCharsets.UTF_8);
                generatedFiles.add(javaFileName);
            }
            writeManifest(proxyDir, context.ownerType, generatedFiles);
        } catch (Exception e) {
            throw new RuntimeException("生成Rpc跨模块代理失败", e);
        }
    }

    private void cleanupOldProxyFiles(Path proxyDir, TypeElement ownerType) throws IOException {
        Path manifestFile = resolveManifestFile(proxyDir, ownerType);
        if (Files.exists(manifestFile)) {
            for (String fileName : Files.readAllLines(manifestFile, StandardCharsets.UTF_8)) {
                if (!fileName.isBlank()) {
                    Files.deleteIfExists(proxyDir.resolve(fileName.trim()));
                }
            }
            Files.deleteIfExists(manifestFile);
        }

        String ownerPackage = ownerType.getQualifiedName().toString();
        final String ownerPackagePrefix = ownerPackage.substring(0, ownerPackage.lastIndexOf('.')) + ".";
        try (var paths = Files.list(proxyDir)) {
            paths.filter(path -> path.getFileName().toString().endsWith("Proxy.java"))
                    .forEach(path -> deleteIfOwnedBy(path, ownerPackagePrefix));
        }
    }

    private void deleteIfOwnedBy(Path path, String ownerPackage) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.contains(ownerPackage)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new RuntimeException("清理旧Rpc代理失败: " + path, e);
        }
    }

    private void writeManifest(Path proxyDir, TypeElement ownerType, List<String> generatedFiles) throws IOException {
        Files.write(proxyDir.resolve(resolveManifestFile(proxyDir, ownerType).getFileName()), generatedFiles, StandardCharsets.UTF_8);
    }

    private Path resolveManifestFile(Path proxyDir, TypeElement ownerType) {
        String ownerFullClassName = ownerType.getQualifiedName().toString().replaceAll("[^A-Za-z0-9]", "_");
        return proxyDir.resolve("_rpc_proxy_manifest_" + ownerFullClassName + ".txt");
    }

    private Path resolveProxyDir() {
        return Paths.get(org.evd.game.gencode.GenConst.ROOT_PROJECT_PATH,
                "common", "src", "gen", "java",
                COMMON_PROXY_PACKAGE.replace('.', java.io.File.separatorChar));
    }
}
