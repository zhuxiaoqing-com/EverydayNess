package org.evd.game.gencode.db;

import javax.annotation.processing.Filer;
import javax.tools.StandardLocation;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class DbDirtyGeneratedJavaFileWriter {
    private final Filer filer;

    DbDirtyGeneratedJavaFileWriter(Filer filer) {
        this.filer = filer;
    }

    void deleteLegacyFiles(DbDirtyEntityMeta entity) {
        deleteJavaFileIfExists(entity.legacyFlatPackage, entity.beanClassName);
        deleteJavaFileIfExists(entity.legacyBrokenPackage, entity.beanClassName);
    }

    void writeJavaFile(String targetPackage, String className, String content) {
        Path javaFile = resolveGenJavaFile(targetPackage, className);
        try {
            Files.createDirectories(javaFile.getParent());
            Files.writeString(javaFile, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("生成DB实体失败: " + javaFile, e);
        }
    }

    private void deleteJavaFileIfExists(String targetPackage, String className) {
        if (targetPackage == null || targetPackage.isEmpty()) {
            return;
        }
        Path javaFile = resolveGenJavaFile(targetPackage, className);
        try {
            Files.deleteIfExists(javaFile);
        } catch (IOException e) {
            throw new RuntimeException("删除旧生成文件失败: " + javaFile, e);
        }
    }

    private Path resolveGenJavaFile(String targetPackage, String className) {
        try {
            String sourceOutputUri = filer.getResource(StandardLocation.SOURCE_OUTPUT, "", className + ".java")
                    .toUri()
                    .toString();
            String normalizedPath = sourceOutputUri.replace('\\', '/');
            String sourceOutputPath = normalizedPath.startsWith("file:/")
                    ? Paths.get(java.net.URI.create(sourceOutputUri)).toString()
                    : sourceOutputUri;

            String normalizedFsPath = sourceOutputPath.replace('\\', '/');
            int buildIndex = normalizedFsPath.indexOf("/build/");
            if (buildIndex < 0) {
                throw new IllegalStateException("无法从 SOURCE_OUTPUT 推导模块根目录: " + sourceOutputPath);
            }

            String moduleRoot = sourceOutputPath.substring(0, buildIndex);
            Path genRoot = Paths.get(moduleRoot, "src", "gen", "java");
            Path packagePath = Paths.get(targetPackage.replace(".", File.separator));
            return genRoot.resolve(packagePath).resolve(className + ".java");
        } catch (IOException e) {
            throw new RuntimeException("解析生成目录失败", e);
        }
    }
}
