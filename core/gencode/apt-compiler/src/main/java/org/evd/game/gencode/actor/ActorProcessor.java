package org.evd.game.gencode.actor;

import com.google.auto.service.AutoService;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.ClientCmdHandler;
import org.evd.game.annotation.ClientCmd;
import org.evd.game.annotation.EventHandler;
import org.evd.game.annotation.Rpc;
import org.evd.game.annotation.RpcHandler;
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
    private static final String EVENT_LISTENER_CLASS_NAME =
            "org.evd.game.runtime.annotation.EventListener";

    private final Set<String> generatedClasses = new LinkedHashSet<>();
    private ServiceOwnerResolver serviceOwnerResolver;

    @Override
    protected Set<String> supportAnnotation() {
        return Set.of(
                Actor.class.getCanonicalName(),
                RpcHandler.class.getCanonicalName(),
                ClientCmdHandler.class.getCanonicalName(),
                EventHandler.class.getCanonicalName()
        );
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
        validateHandlerAnnotations(roundEnv);
        TypeElement eventListenerType = elementUtils.getTypeElement(EVENT_LISTENER_CLASS_NAME);
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
            validateActorNaming(typeElement, eventListenerType);
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

    private void validateHandlerAnnotations(RoundEnvironment roundEnv) {
        validateClassAnnotations(roundEnv.getElementsAnnotatedWith(RpcHandler.class), "@RpcHandler");
        validateClassAnnotations(roundEnv.getElementsAnnotatedWith(ClientCmdHandler.class), "@ClientCmdHandler");

        TypeElement eventListenerType = elementUtils.getTypeElement(EVENT_LISTENER_CLASS_NAME);
        for (Element element : roundEnv.getElementsAnnotatedWith(EventHandler.class)) {
            if (!(element instanceof TypeElement typeElement)) {
                throw new IllegalStateException("@EventHandler 只能标记在类上: " + element);
            }
            if (typeElement.getAnnotation(Actor.class) == null) {
                throw new IllegalStateException(typeElement.getQualifiedName()
                        + " 标注了 @EventHandler，但未标注 @Actor");
            }
            if (eventListenerType == null || !implementsEventListener(typeElement, eventListenerType)) {
                throw new IllegalStateException(typeElement.getQualifiedName()
                        + " 标注了 @EventHandler，但未实现 EventListener 接口");
            }
        }

        if (eventListenerType == null) {
            return;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(Actor.class)) {
            if (!(element instanceof TypeElement typeElement)
                    || !implementsEventListener(typeElement, eventListenerType)) {
                continue;
            }
            if (typeElement.getAnnotation(EventHandler.class) == null) {
                throw new IllegalStateException(typeElement.getQualifiedName()
                        + " 实现了 EventListener，但未标注 @EventHandler");
            }
        }
    }

    private void validateClassAnnotations(Set<? extends Element> elements, String annotationName) {
        for (Element element : elements) {
            if (!(element instanceof TypeElement typeElement)
                    || typeElement.getKind() != ElementKind.CLASS) {
                throw new IllegalStateException(annotationName + " 只能标记在具体 class 上: " + element);
            }
            if (typeElement.getAnnotation(Actor.class) == null) {
                throw new IllegalStateException(typeElement.getQualifiedName()
                        + " 标注了 " + annotationName + "，但未标注 @Actor");
            }
        }
    }

    private void validateActorNaming(TypeElement typeElement, TypeElement eventListenerType) {
        boolean rpcHandler = typeElement.getAnnotation(RpcHandler.class) != null;
        boolean clientCmdHandler = typeElement.getAnnotation(ClientCmdHandler.class) != null;
        boolean eventHandler = typeElement.getAnnotation(EventHandler.class) != null;
        int handlerCount = (rpcHandler ? 1 : 0) + (clientCmdHandler ? 1 : 0) + (eventHandler ? 1 : 0);
        boolean hasRpcMethod = hasMethodAnnotation(typeElement, Rpc.class);
        boolean hasClientCmdMethod = hasMethodAnnotation(typeElement, ClientCmd.class);
        boolean hasEventListener = eventListenerType != null && implementsEventListener(typeElement, eventListenerType);
        if (rpcHandler != hasRpcMethod || clientCmdHandler != hasClientCmdMethod || eventHandler != hasEventListener) {
            throw new IllegalStateException(typeElement.getQualifiedName()
                    + " 的 Handler 标识与实际入口不匹配: rpc=" + hasRpcMethod
                    + ", clientCmd=" + hasClientCmdMethod
                    + ", event=" + hasEventListener);
        }
        String suffix;
        if (handlerCount == 0) {
            suffix = "Logic";
        } else if (handlerCount > 1) {
            suffix = "Handler";
        } else if (rpcHandler) {
            suffix = "Rpc";
        } else if (clientCmdHandler) {
            suffix = "ClientCmd";
        } else {
            suffix = "Listener";
        }
        String className = typeElement.getSimpleName().toString();
        if (!className.endsWith(suffix)) {
            throw new IllegalStateException(typeElement.getQualifiedName()
                    + " 的命名不符合 Handler 标识规则，应使用 " + suffix + " 后缀");
        }
    }

    private boolean implementsEventListener(TypeElement typeElement, TypeElement eventListenerType) {
        for (TypeMirror interfaceType : typeElement.getInterfaces()) {
            if (typeUtils.isSubtype(interfaceType, eventListenerType.asType())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMethodAnnotation(TypeElement typeElement,
                                        Class<? extends java.lang.annotation.Annotation> annotationType) {
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement instanceof ExecutableElement executableElement
                    && executableElement.getAnnotation(annotationType) != null) {
                return true;
            }
        }
        return false;
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
        source.append("import org.evd.game.runtime.actorLogic.ActorManager;\n");
        source.append("import org.evd.game.runtime.support.exception.SysException;\n");
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
        source.append("    }\n\n");
        source.append("    @Override\n");
        source.append("    public Map<Class<?>, Object> getActors() {\n");
        source.append("        return actors;\n");
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
