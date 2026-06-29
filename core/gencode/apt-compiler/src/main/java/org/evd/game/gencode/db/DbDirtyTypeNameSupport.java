package org.evd.game.gencode.db;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DbDirtyTypeNameSupport {
    private DbDirtyTypeNameSupport() {
    }

    static Map<String, String> collectImportedEntityTypes(DbDirtyEntityMeta entity) {
        Set<String> qualifiedTypes = new LinkedHashSet<>();
        for (DbDirtyFieldMeta field : entity.fields) {
            field.type.collectQualifiedEntityTypes(qualifiedTypes);
        }

        Map<String, Integer> simpleNameCount = new LinkedHashMap<>();
        for (String qualifiedType : qualifiedTypes) {
            simpleNameCount.merge(simpleNameOf(qualifiedType), 1, Integer::sum);
        }

        Map<String, String> importedTypes = new LinkedHashMap<>();
        for (String qualifiedType : qualifiedTypes) {
            String packageName = packageNameOf(qualifiedType);
            String simpleName = simpleNameOf(qualifiedType);
            if (packageName.equals(entity.beanPackage)) {
                continue;
            }
            if (simpleNameCount.getOrDefault(simpleName, 0) > 1) {
                continue;
            }
            importedTypes.put(qualifiedType, simpleName);
        }
        return importedTypes;
    }

    static void appendImports(StringBuilder sb, Map<String, String> importedTypes) {
        for (String qualifiedType : importedTypes.keySet()) {
            sb.append("import ").append(qualifiedType).append(";\n");
        }
    }

    static String rewriteImportedTypeNames(String content, Map<String, String> importedTypes) {
        List<Map.Entry<String, String>> entries = new ArrayList<>(importedTypes.entrySet());
        entries.sort(Comparator.comparingInt((Map.Entry<String, String> entry) -> entry.getKey().length()).reversed());

        StringBuilder rewritten = new StringBuilder(content.length());
        String[] lines = content.split("\n", -1);
        for (String line : lines) {
            String currentLine = line;
            String trimmed = line.trim();
            if (!trimmed.startsWith("package ") && !trimmed.startsWith("import ")) {
                for (Map.Entry<String, String> entry : entries) {
                    currentLine = currentLine.replace(entry.getKey(), entry.getValue());
                }
            }
            rewritten.append(currentLine).append('\n');
        }
        return rewritten.toString();
    }

    private static String packageNameOf(String qualifiedType) {
        int lastDot = qualifiedType.lastIndexOf('.');
        return lastDot < 0 ? "" : qualifiedType.substring(0, lastDot);
    }

    private static String simpleNameOf(String qualifiedType) {
        int lastDot = qualifiedType.lastIndexOf('.');
        return lastDot < 0 ? qualifiedType : qualifiedType.substring(lastDot + 1);
    }
}
