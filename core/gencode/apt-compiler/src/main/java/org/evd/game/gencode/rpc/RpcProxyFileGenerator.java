package org.evd.game.gencode.rpc;

import org.evd.game.annotation.actor.Rpc;
import org.evd.game.gencode.GenConst;
import org.evd.game.gencode.struct.MethodStruct;

import javax.lang.model.element.TypeElement;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

final class RpcProxyFileGenerator {
    private static final String COMMON_PROXY_PACKAGE = "org.evd.game.common.proxy";
    private static final String SERVICE_PACKAGE_PREFIX = "org.evd.game.";

    private final RpcSupport support;

    RpcProxyFileGenerator(RpcSupport support) {
        this.support = support;
    }

    String generatedProxyFileName(TypeElement typeElement) {
        return typeElement.getSimpleName() + "Proxy.java";
    }

    void generate(TypeElement typeElement, List<MethodStruct<Rpc>> methods) {
        try {
            String generatedClassName = generatedProxyFileName(typeElement);
            Path proxyRootDir = resolveProxyRootDir();
            Path serviceProxyDir = resolveServiceProxyDir(proxyRootDir, typeElement);
            Files.createDirectories(serviceProxyDir);

            String content = support.renderTemplate(RpcSupport.TEMPLATE_RPC_PROXY, support.buildProxyRootMap(methods));
            writeIfChanged(serviceProxyDir.resolve(generatedClassName), content);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("生成Rpc跨模块代理失败", e);
        }
    }

    void cleanupStaleServiceProxies(TypeElement ownerType, Set<String> expectedProxyFiles) {
        try {
            Path proxyRootDir = resolveProxyRootDir();
            Path serviceProxyDir = resolveServiceProxyDir(proxyRootDir, ownerType);
            Files.createDirectories(serviceProxyDir);
            cleanupStaleProxyFiles(serviceProxyDir, expectedProxyFiles);
            cleanupMisplacedOwnerProxyDir(proxyRootDir, serviceProxyDir, ownerType);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("清理Rpc代理目录失败", e);
        }
    }

    private void cleanupStaleProxyFiles(Path serviceProxyDir, Set<String> expectedProxyFiles)
            throws IOException {
        List<Path> staleFiles;
        try (Stream<Path> pathStream = Files.list(serviceProxyDir)) {
            staleFiles = pathStream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("Proxy.java"))
                    .filter(path -> !expectedProxyFiles.contains(path.getFileName().toString()))
                    .toList();
        }
        for (Path staleFile : staleFiles) {
            deleteQuietly(staleFile);
        }
    }

    private void cleanupMisplacedOwnerProxyDir(Path proxyRootDir, Path serviceProxyDir, TypeElement ownerType) throws IOException {
        Path misplacedOwnerDir = proxyRootDir.resolve(ownerType.getSimpleName().toString()).toAbsolutePath().normalize();
        if (misplacedOwnerDir.equals(serviceProxyDir.toAbsolutePath().normalize()) || !Files.exists(misplacedOwnerDir)) {
            return;
        }
        cleanupStaleProxyFiles(misplacedOwnerDir, Set.of());
        try (Stream<Path> remaining = Files.list(misplacedOwnerDir)) {
            if (remaining.findAny().isEmpty()) {
                deleteQuietly(misplacedOwnerDir);
            }
        }
    }

    private void writeIfChanged(Path filePath, String content) throws IOException {
        if (Files.exists(filePath)) {
            String oldContent = Files.readString(filePath);
            if (oldContent.equals(content)) {
                return;
            }
        }
        Files.writeString(filePath, content);
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new RuntimeException("清理旧Rpc代理文件失败: " + path, e);
        }
    }

    private Path resolveServiceProxyDir(Path proxyRootDir, TypeElement ownerType) {
        return proxyRootDir.resolve(resolveServiceDirName(ownerType));
    }

    private String resolveServiceDirName(TypeElement ownerType) {
        String ownerQualifiedName = ownerType.getQualifiedName().toString();
        if (!ownerQualifiedName.startsWith(SERVICE_PACKAGE_PREFIX)) {
            throw new IllegalStateException("Rpc代理归属的Service目录无法解析: " + ownerQualifiedName);
        }
        String remain = ownerQualifiedName.substring(SERVICE_PACKAGE_PREFIX.length());
        int separatorIndex = remain.indexOf('.');
        if (separatorIndex <= 0) {
            throw new IllegalStateException("Rpc代理归属的Service目录无法解析: " + ownerQualifiedName);
        }
        return remain.substring(0, separatorIndex);
    }

    private Path resolveProxyRootDir() {
        return Paths.get(GenConst.ROOT_PROJECT_PATH,
                "common", "src", "gen", "java",
                COMMON_PROXY_PACKAGE.replace('.', File.separatorChar));
    }
}
