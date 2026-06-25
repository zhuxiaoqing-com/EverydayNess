package org.evd.game.gencode.db;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;

final class DbDirtyGeneratedJavaFileWriter {
    private final Filer filer;

    DbDirtyGeneratedJavaFileWriter(Filer filer) {
        this.filer = filer;
    }

    void writeJavaFile(String targetPackage, String className, String content, TypeElement sourceType) {
        writeJavaFile(targetPackage, className, content, new Element[]{sourceType});
    }

    void writeJavaFile(String targetPackage, String className, String content, Element... originatingElements) {
        try {
            JavaFileObject sourceFile = filer.createSourceFile(targetPackage + "." + className, originatingElements);
            try (Writer writer = sourceFile.openWriter()) {
                writer.write(content);
            }
        } catch (IOException e) {
            throw new RuntimeException("生成DB实体失败: " + targetPackage + "." + className, e);
        }
    }
}
