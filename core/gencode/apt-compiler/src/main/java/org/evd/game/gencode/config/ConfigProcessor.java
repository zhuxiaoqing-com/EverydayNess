package org.evd.game.gencode.config;

import com.google.auto.service.AutoService;
import org.evd.game.annotation.config.Column;
import org.evd.game.annotation.config.Config;
import org.evd.game.annotation.config.ConfigDefaultConverter;
import org.evd.game.annotation.config.Converter;
import org.evd.game.gencode.ProcessorBase;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 生成配置访问门面和统一配置表注册入口，不生成逐字段 CSV 解析代码。 */
@AutoService(Processor.class)
public final class ConfigProcessor extends ProcessorBase {
    private static final String RUNTIME = "org.evd.game.runtime.config.";
    private static final String TABLE_LOADER_PACKAGE = "org.evd.game.common.config.table";
    private static final String TABLE_LOADER_NAME = "TableRegistry";

    private final Set<String> generatedAccessors = new LinkedHashSet<>();
    private boolean generatedTableLoader;

    @Override
    protected Set<String> supportAnnotation() {
        return Set.of(Config.class.getCanonicalName(), ConfigDefaultConverter.class.getCanonicalName());
    }

    @Override
    protected void init() {
    }

    @Override
    protected void gen(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Map<String, String> defaultConverters = collectDefaultConverters(roundEnv);
        List<TypeElement> configTypes = new ArrayList<>();
        for (Element element : roundEnv.getElementsAnnotatedWith(Config.class)) {
            if (!(element instanceof TypeElement type)) {
                error(element, "@Config 只能标注在类上");
                continue;
            }
            if (!validateConfig(type)) {
                continue;
            }
            generateAccessor(type);
            configTypes.add(type);
        }
        if (!configTypes.isEmpty() && !generatedTableLoader) {
            generateTableLoader(configTypes, defaultConverters);
            generatedTableLoader = true;
        }
    }

    private Map<String, String> collectDefaultConverters(RoundEnvironment roundEnv) {
        Map<String, String> converters = new HashMap<>();
        for (Element element : roundEnv.getElementsAnnotatedWith(ConfigDefaultConverter.class)) {
            String targetType = annotationTypeValue(element, ConfigDefaultConverter.class.getCanonicalName());
            if (targetType != null && element instanceof TypeElement converter) {
                converters.put(targetType, converter.getQualifiedName().toString());
            }
        }
        return converters;
    }

    private boolean validateConfig(TypeElement type) {
        boolean valid = true;
        Config config = type.getAnnotation(Config.class);
        if (type.getKind() != ElementKind.CLASS) {
            error(type, "@Config 只能标注在具体 class 上");
            valid = false;
        }
        Set<String> fieldNames = new LinkedHashSet<>();
        for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
            if (!field.getModifiers().contains(Modifier.STATIC)) {
                fieldNames.add(field.getSimpleName().toString());
            }
            if (field.getModifiers().contains(Modifier.FINAL)) {
                error(field, "@Config 字段不能是 final: " + field.getSimpleName());
                valid = false;
            }
        }
        Set<String> keys = new LinkedHashSet<>();
        for (String key : config.keys()) {
            if (!keys.add(key)) {
                error(type, "@Config keys 不能重复: " + key);
                valid = false;
            }
            if (!fieldNames.contains(key)) {
                error(type, "@Config key 字段不存在: " + key);
                valid = false;
            }
        }
        if (config.keys().length == 0) {
            error(type, "@Config 至少需要一个 key 字段");
            valid = false;
        }
        return valid;
    }

    private void generateAccessor(TypeElement configType) {
        String packageName = elementUtils.getPackageOf(configType).getQualifiedName().toString();
        String configName = configType.getSimpleName().toString();
        String accessorName = configName + "s";
        String fullName = packageName + "." + accessorName;
        if (!generatedAccessors.add(fullName)) {
            return;
        }

        Config config = configType.getAnnotation(Config.class);
        Map<String, VariableElement> fields = new HashMap<>();
        for (VariableElement field : ElementFilter.fieldsIn(configType.getEnclosedElements())) {
            fields.put(field.getSimpleName().toString(), field);
        }

        StringBuilder source = new StringBuilder();
        source.append("package ").append(packageName).append(";\n\n")
                .append("import java.util.Collection;\n")
                .append("import ").append(RUNTIME).append("ConfigManager;\n\n")
                .append("public final class ").append(accessorName).append(" {\n")
                .append("    private ").append(accessorName).append("() {}\n\n");

        StringBuilder parameters = new StringBuilder();
        StringBuilder arguments = new StringBuilder();
        for (int i = 0; i < config.keys().length; i++) {
            VariableElement field = fields.get(config.keys()[i]);
            if (i > 0) {
                parameters.append(", ");
                arguments.append(", ");
            }
            parameters.append(field.asType()).append(' ').append(field.getSimpleName());
            arguments.append(field.getSimpleName());
        }
        boolean singleIntKey = config.keys().length == 1
                && fields.get(config.keys()[0]).asType().toString().equals("int");
        String getMethod = singleIntKey ? "getInt" : "get";
        source.append("    public static ").append(configName).append(" get(")
                .append(parameters).append(") {\n")
                .append("        return ConfigManager.").append(getMethod).append("(")
                .append(configName).append(".class, ").append(arguments).append(");\n")
                .append("    }\n\n")
                .append("    public static Collection<").append(configName).append("> getAll() {\n")
                .append("        return ConfigManager.getAll(")
                .append(configName).append(".class);\n")
                .append("    }\n")
                .append("}\n");
        writeSource(fullName, source, configType, "生成配置访问类失败");
    }

    private void generateTableLoader(List<TypeElement> configTypes, Map<String, String> defaultConverters) {
        String fullName = TABLE_LOADER_PACKAGE + "." + TABLE_LOADER_NAME;
        StringBuilder source = new StringBuilder();
        Set<String> imports = new LinkedHashSet<>();
        imports.add(RUNTIME + "ConfigManager");
        imports.add(RUNTIME + "ConfigTableRegistry");
        imports.add("org.evd.game.annotation.config.ConfigConverter");
        imports.add("java.util.Map");
        source.append("package ").append(TABLE_LOADER_PACKAGE).append(";\n\n")
                .append(renderImports(imports))
                .append("public final class ").append(TABLE_LOADER_NAME)
                .append(" implements ConfigTableRegistry {\n")
                .append("    @Override\n")
                .append("    public Class<?>[] configTypes() {\n")
                .append("        return new Class<?>[]{");
        for (int i = 0; i < configTypes.size(); i++) {
            if (i > 0) {
                source.append(", ");
            }
            source.append(configTypes.get(i).getQualifiedName()).append(".class");
        }
        source.append("};\n")
                .append("    }\n\n")
                .append("    @Override\n")
                .append("    public Map<Class<?>, Class<? extends ConfigConverter<?>>> defaultConverters() {\n")
                .append("        return Map.ofEntries(");
        int converterIndex = 0;
        for (Map.Entry<String, String> entry : defaultConverters.entrySet()) {
            if (converterIndex++ > 0) {
                source.append(", ");
            }
            source.append("Map.entry(")
                    .append(entry.getKey()).append(".class, ")
                    .append(entry.getValue()).append(".class)");
        }
        source.append(");\n")
                .append("    }\n")
                .append("}\n");
        writeSource(fullName, source, configTypes.toArray(Element[]::new), "生成配置表加载器失败");
    }

    private String renderImports(Set<String> imports) {
        StringBuilder source = new StringBuilder();
        for (String importedType : imports) {
            source.append("import ").append(importedType).append(";\n");
        }
        return source.append('\n').toString();
    }

    private void writeSource(String fullName, StringBuilder source, Element originatingElement,
                             String errorMessage) {
        writeSource(fullName, source, new Element[]{originatingElement}, errorMessage);
    }

    private void writeSource(String fullName, StringBuilder source, Element[] originatingElements,
                             String errorMessage) {
        try {
            JavaFileObject file = filer.createSourceFile(fullName, originatingElements);
            try (Writer writer = file.openWriter()) {
                writer.write(source.toString());
            }
        } catch (IOException e) {
            throw new IllegalStateException(errorMessage + ": " + fullName, e);
        }
    }

    private String annotationTypeValue(Element element, String annotationName) {
        for (var annotation : element.getAnnotationMirrors()) {
            if (!annotation.getAnnotationType().toString().equals(annotationName)) {
                continue;
            }
            for (var entry : elementUtils.getElementValuesWithDefaults(annotation).entrySet()) {
                if (!entry.getKey().getSimpleName().contentEquals("value")) {
                    continue;
                }
                Object value = entry.getValue().getValue();
                if (value instanceof TypeMirror mirror) {
                    return mirror.toString();
                }
            }
        }
        return null;
    }

    private void error(Element element, String message) {
        if (element == null) {
            messager.printMessage(javax.tools.Diagnostic.Kind.ERROR, message);
        } else {
            messager.printMessage(javax.tools.Diagnostic.Kind.ERROR, message, element);
        }
    }
}
