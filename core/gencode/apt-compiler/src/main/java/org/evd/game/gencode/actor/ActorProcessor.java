package org.evd.game.gencode.actor;

import com.google.auto.service.AutoService;
import org.evd.game.annotation.Actor;
import org.evd.game.gencode.ProcessorBase;
import org.evd.game.gencode.ServiceOwnerResolver;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@AutoService(Processor.class)
public class ActorProcessor extends ProcessorBase {
    private static final Set<String> ALLOWED_BOXED_TYPES = Set.of(
            Boolean.class.getCanonicalName(),
            Byte.class.getCanonicalName(),
            Short.class.getCanonicalName(),
            Integer.class.getCanonicalName(),
            Long.class.getCanonicalName(),
            Float.class.getCanonicalName(),
            Double.class.getCanonicalName(),
            Character.class.getCanonicalName(),
            String.class.getCanonicalName()
    );

    private final Set<String> generatedClasses = new LinkedHashSet<>();
    private ServiceOwnerResolver serviceOwnerResolver;

    @Override
    protected Set<String> supportAnnotation() {
        return Collections.singleton(Actor.class.getCanonicalName());
    }

    @Override
    protected void init() {
        serviceOwnerResolver = new ServiceOwnerResolver(elementUtils, typeUtils);
    }

    @Override
    protected void gen(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        println("");
        println("开始执行Actor Processor");

        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Actor.class);
        if (elements == null || elements.isEmpty()) {
            return;
        }

        Map<String, List<ActorTarget>> serviceActors = new LinkedHashMap<>();
        Map<String, TypeElement> serviceOwners = new LinkedHashMap<>();
        for (Element element : elements) {
            if (!(element instanceof TypeElement typeElement)) {
                throw new IllegalStateException("@Actor 只能标记在类上: " + element);
            }
            validateActorType(typeElement);
            if (serviceOwnerResolver.isServiceType(typeElement)) {
                serviceOwners.put(typeElement.getQualifiedName().toString(), typeElement);
                continue;
            }

            TypeElement serviceOwner = serviceOwnerResolver.resolve(typeElement);
            serviceOwners.put(serviceOwner.getQualifiedName().toString(), serviceOwner);
            serviceActors.computeIfAbsent(serviceOwner.getQualifiedName().toString(), key -> new ArrayList<>())
                    .add(toActorTarget(typeElement));
        }

        for (Map.Entry<String, List<ActorTarget>> entry : serviceActors.entrySet()) {
            TypeElement serviceOwner = serviceOwners.get(entry.getKey());
            List<ActorTarget> actorTargets = entry.getValue();
            actorTargets.sort(Comparator.comparing(target -> target.fullClassName));
            generateActorManager(serviceOwner, actorTargets);
        }
    }

    private void validateActorType(TypeElement typeElement) {
        if (typeElement.getKind() != ElementKind.CLASS) {
            throw new IllegalStateException("@Actor 只能标记具体 class: " + typeElement.getQualifiedName());
        }
        if (typeElement.getModifiers().contains(Modifier.ABSTRACT)) {
            throw new IllegalStateException("@Actor 不能标记抽象类: " + typeElement.getQualifiedName());
        }
        if (typeElement.getNestingKind().isNested() && !typeElement.getModifiers().contains(Modifier.STATIC)) {
            throw new IllegalStateException("@Actor 内部类必须声明为 static: " + typeElement.getQualifiedName());
        }
        validateNoArgConstructor(typeElement);
        validateStaticFields(typeElement);
    }

    private void validateNoArgConstructor(TypeElement typeElement) {
        List<ExecutableElement> constructors = new ArrayList<>();
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.CONSTRUCTOR) {
                constructors.add((ExecutableElement) enclosedElement);
            }
        }
        if (constructors.isEmpty()) {
            return;
        }
        for (ExecutableElement constructor : constructors) {
            if (!constructor.getParameters().isEmpty()) {
                continue;
            }
            if (constructor.getModifiers().contains(Modifier.PRIVATE)) {
                throw new IllegalStateException("@Actor 默认构造函数不能是 private: " + typeElement.getQualifiedName());
            }
            return;
        }
        throw new IllegalStateException("@Actor 必须提供无参构造函数: " + typeElement.getQualifiedName());
    }

    private void validateStaticFields(TypeElement typeElement) {
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.FIELD) {
                continue;
            }
            if (!enclosedElement.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            if (enclosedElement.getModifiers().contains(Modifier.FINAL)
                    && isAllowedStaticFinalType(enclosedElement.asType())) {
                continue;
            }
            throw new IllegalStateException("@Actor 不允许声明 static 数据: "
                    + typeElement.getQualifiedName() + "#" + enclosedElement.getSimpleName());
        }
    }

    private boolean isAllowedStaticFinalType(TypeMirror typeMirror) {
        if (typeMirror.getKind().isPrimitive()) {
            return true;
        }
        Element element = typeUtils.asElement(typeMirror);
        if (!(element instanceof TypeElement typeElement)) {
            return false;
        }
        if (typeElement.getKind() == ElementKind.ENUM) {
            return true;
        }
        return ALLOWED_BOXED_TYPES.contains(typeElement.getQualifiedName().toString());
    }

    private ActorTarget toActorTarget(TypeElement actorType) {
        PackageElement packageElement = elementUtils.getPackageOf(actorType);
        return new ActorTarget(
                packageElement.getQualifiedName().toString(),
                actorType.getSimpleName().toString(),
                actorType.getQualifiedName().toString(),
                actorType
        );
    }

    private void generateActorManager(TypeElement serviceOwner, List<ActorTarget> actorTargets) {
        String packageName = elementUtils.getPackageOf(serviceOwner).getQualifiedName().toString();
        String className = serviceOwner.getSimpleName() + "ActorManager";
        String fullClassName = packageName + "." + className;
        if (!generatedClasses.add(fullClassName)) {
            return;
        }

        try {
            JavaFileObject sourceFile = filer.createSourceFile(fullClassName, collectOriginatingElements(serviceOwner, actorTargets));
            try (Writer writer = sourceFile.openWriter()) {
                writer.write(buildActorManagerSource(packageName, className, actorTargets));
            }
            println("generate success [" + className + ".java]");
        } catch (Exception e) {
            throw new RuntimeException("生成 ActorManager 失败: " + fullClassName, e);
        }
    }

    private String buildActorManagerSource(String packageName, String className, List<ActorTarget> actorTargets) {
        StringBuilder source = new StringBuilder();
        source.append("package ").append(packageName).append(";\n\n");
        source.append("import org.evd.game.runtime.ActorManager;\n");
        source.append("import org.evd.game.runtime.support.SysException;\n");
        source.append("import java.util.LinkedHashMap;\n");
        source.append("import java.util.Map;\n");
        for (String importType : collectImports(packageName, actorTargets)) {
            source.append("import ").append(importType).append(";\n");
        }
        source.append("\n");
        source.append("/**\n");
        source.append(" * 根据 @Actor 自动生成的实例注册表\n");
        source.append(" */\n");
        source.append("public final class ").append(className).append(" implements ActorManager {\n");
        source.append("    private final Map<Class<?>, Object> actors = new LinkedHashMap<>();\n\n");
        source.append("    public ").append(className).append("() {\n");
        for (ActorTarget actorTarget : actorTargets) {
            source.append("        register(")
                    .append(actorTarget.className)
                    .append(".class, new ")
                    .append(actorTarget.className)
                    .append("());\n");
        }
        source.append("    }\n\n");
        source.append("    private <T> void register(Class<T> actorType, T actor) {\n");
        source.append("        Object previous = actors.putIfAbsent(actorType, actor);\n");
        source.append("        if (previous != null) {\n");
        source.append("            throw new SysException(\"duplicate actor registration: {}\", actorType.getName());\n");
        source.append("        }\n");
        source.append("    }\n\n");
        source.append("    @Override\n");
        source.append("    public <T> T getActor(Class<T> actorType) {\n");
        source.append("        Object actor = actors.get(actorType);\n");
        source.append("        if (actor == null) {\n");
        source.append("            throw new SysException(\"actor not found: {}\", actorType.getName());\n");
        source.append("        }\n");
        source.append("        return actorType.cast(actor);\n");
        source.append("    }\n");
        source.append("}\n");
        return source.toString();
    }

    private List<String> collectImports(String packageName, List<ActorTarget> actorTargets) {
        Set<String> imports = new LinkedHashSet<>();
        for (ActorTarget actorTarget : actorTargets) {
            if (!actorTarget.packageName.equals(packageName)) {
                imports.add(actorTarget.fullClassName);
            }
        }
        return new ArrayList<>(imports);
    }

    private Element[] collectOriginatingElements(TypeElement serviceOwner, List<ActorTarget> actorTargets) {
        LinkedHashSet<Element> elements = new LinkedHashSet<>();
        elements.add(serviceOwner);
        for (ActorTarget actorTarget : actorTargets) {
            elements.add(actorTarget.typeElement);
        }
        return elements.toArray(Element[]::new);
    }

    private record ActorTarget(
            String packageName,
            String className,
            String fullClassName,
            TypeElement typeElement
    ) {
    }
}
