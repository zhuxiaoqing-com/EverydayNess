package org.evd.game.gencode.client;

import com.google.auto.service.AutoService;
import org.evd.game.annotation.ClientCmd;
import org.evd.game.gencode.ProcessorBase;
import org.evd.game.gencode.ServiceOwnerResolver;
import org.evd.game.annotation.ActorType;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@AutoService(Processor.class)
public class ClientCmdProcessor extends ProcessorBase {

    private static final String CLIENT_SESSION_REF_CLASS_NAME = "org.evd.game.runtime.client.ClientSessionRef";
    private static final String CLIENT_CMD_REGISTRY_BASE_CLASS_NAME = "org.evd.game.runtime.client.ClientCmdRegistryBase";
    private static final String CLIENT_CMD_ROUTE_TABLE_CLASS_NAME = "org.evd.game.runtime.client.ClientCmdRouteTable";
    private static final String PROTO_MESSAGE_CLASS_NAME = "com.google.protobuf.MessageLite";
    private static final String ACTOR_TYPE_CLASS_NAME = "org.evd.game.runtime.actor.ActorType";

    private final Set<String> generatedClasses = new HashSet<>();
    private ServiceOwnerResolver serviceOwnerResolver;

    @Override
    protected Set<String> supportAnnotation() {
        return Collections.singleton(ClientCmd.class.getCanonicalName());
    }

    @Override
    protected void init() {
        serviceOwnerResolver = new ServiceOwnerResolver(elementUtils, typeUtils);
    }

    @Override
    protected void gen(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        println("");
        println("开始执行ClientCmd Processor");

        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(ClientCmd.class);
        if (elements == null || elements.isEmpty()) {
            return;
        }

        TypeElement sessionType = requireType(CLIENT_SESSION_REF_CLASS_NAME);
        TypeElement protoMessageType = requireType(PROTO_MESSAGE_CLASS_NAME);
        Map<String, List<ClientCmdMethod>> classMethods = new LinkedHashMap<>();
        for (Element element : elements) {
            if (!(element instanceof ExecutableElement executableElement)) {
                throw new IllegalStateException("@ClientCmd 只能标记在方法上: " + element);
            }
            ClientCmdMethod method = parseMethod(executableElement, sessionType, protoMessageType);
            classMethods.computeIfAbsent(method.serviceOwnerFullClassName, key -> new ArrayList<>()).add(method);
        }

        classMethods.values().forEach(methods -> {
            methods.sort(Comparator.comparingInt((ClientCmdMethod method) -> method.cmd).thenComparing(method -> method.methodName));
            checkDuplicateCmd(methods);
            genRegistry(methods);
            genRouteRegistry(methods);
        });
    }

    private void genRegistry(List<ClientCmdMethod> methods) {
        ClientCmdMethod first = methods.getFirst();
        String className = first.serviceOwnerClassName + "ClientCmdRegistry";
        String packageName = first.serviceOwnerPackageName;
        String fullClassName = packageName + "." + className;
        if (!generatedClasses.add(fullClassName)) {
            return;
        }

        try {
            writeJavaSource(fullClassName,
                    buildRegistrySource(first.serviceOwnerClassName, className, packageName, methods),
                    collectOriginatingElements(methods));
            println("generate success [" + className + ".java]");
        } catch (Exception e) {
            throw new RuntimeException("生成客户端协议分发表失败: " + className, e);
        }
    }

    private void genRouteRegistry(List<ClientCmdMethod> methods) {
        ClientCmdMethod first = methods.getFirst();
        String className = first.serviceOwnerClassName + "ClientCmdRouteRegistry";
        String packageName = first.serviceOwnerPackageName;
        String fullClassName = packageName + "." + className;
        if (!generatedClasses.add(fullClassName)) {
            return;
        }

        try {
            writeJavaSource(fullClassName,
                    buildRouteRegistrySource(className, methods),
                    collectOriginatingElements(methods));
            println("generate success [" + className + ".java]");
        } catch (Exception e) {
            throw new RuntimeException("生成客户端协议路由注册类失败: " + className, e);
        }
    }

    private String buildRegistrySource(String ownerClassName,
                                       String className,
                                       String packageName,
                                       List<ClientCmdMethod> methods) {
        StringBuilder source = new StringBuilder();
        source.append("package ").append(packageName).append(";\n\n");
        source.append("import com.google.protobuf.InvalidProtocolBufferException;\n");
        source.append("import ").append(CLIENT_CMD_REGISTRY_BASE_CLASS_NAME).append(";\n");
        source.append("import ").append(CLIENT_SESSION_REF_CLASS_NAME).append(";\n");
        for (String importPackage : collectImports(methods, packageName)) {
            source.append("import ").append(importPackage).append(";\n");
        }
        source.append("\n");
        source.append("/**\n");
        source.append(" * 根据").append(ownerClassName).append("生成的客户端协议分发类\n");
        source.append(" */\n");
        source.append("public final class ").append(className)
                .append(" extends ClientCmdRegistryBase<").append(ownerClassName).append("> {\n");
        for (String fieldLine : buildTargetFieldLines(methods)) {
            source.append(fieldLine);
        }
        if (hasNonOwnerTarget(methods)) {
            source.append("\n");
        }
        source.append("    public ").append(className).append("(").append(ownerClassName).append(" owner) {\n");
        source.append("        super(owner);\n");
        source.append("    }\n\n");
        source.append("    @Override\n");
        source.append("    public void dispatch(ClientSessionRef session, int cmd, byte[] body) throws InvalidProtocolBufferException {\n");
        source.append("        switch (cmd) {\n");
        for (ClientCmdMethod method : methods) {
            source.append("            case ").append(method.cmdExpr).append(":\n");
            source.append("                ").append(method.dispatchTargetExpr()).append(".").append(method.methodName)
                    .append("(session, ").append(method.requestClassName).append(".parseFrom(body));\n");
            source.append("                return;\n");
        }
        source.append("            default:\n");
        source.append("                throw new IllegalArgumentException(\"unknown client cmd: \" + cmd);\n");
        source.append("        }\n");
        source.append("    }\n");
        source.append("}\n");
        return source.toString();
    }

    private String buildRouteRegistrySource(String className, List<ClientCmdMethod> methods) {
        ClientCmdMethod first = methods.getFirst();
        StringBuilder source = new StringBuilder();
        source.append("package ").append(first.serviceOwnerPackageName).append(";\n\n");
        source.append("import ").append(CLIENT_CMD_ROUTE_TABLE_CLASS_NAME).append(";\n");
        source.append("import ").append(ACTOR_TYPE_CLASS_NAME).append(";\n\n");
        source.append("/**\n");
        source.append(" * 根据").append(first.serviceOwnerClassName).append("生成的客户端协议路由注册类\n");
        source.append(" */\n");
        source.append("public final class ").append(className).append(" {\n");
        source.append("    private ").append(className).append("() {\n");
        source.append("    }\n\n");
        source.append("    public static void register(ClientCmdRouteTable routeTable) {\n");
        for (ClientCmdMethod method : methods) {
            source.append("        routeTable.register(")
                    .append(method.cmdExpr)
                    .append(", \"")
                    .append(method.serviceOwnerFullClassName)
                    .append("\", ActorType.")
                    .append(method.actorType.name())
                    .append(");\n");
        }
        source.append("    }\n");
        source.append("}\n");
        return source.toString();
    }

    private List<String> collectImports(List<ClientCmdMethod> methods, String packageName) {
        Set<String> imports = new LinkedHashSet<>();
        for (ClientCmdMethod method : methods) {
            if (!method.requestPackageName.equals(packageName)) {
                imports.add(method.requestTypeName);
            }
            if (!method.targetPackageName.equals(packageName) && !method.isServiceOwnerTarget()) {
                imports.add(method.targetTypeName());
            }
        }
        return new ArrayList<>(imports);
    }

    private void checkDuplicateCmd(List<ClientCmdMethod> methods) {
        Map<Integer, ClientCmdMethod> cmdOwners = new LinkedHashMap<>();
        for (ClientCmdMethod method : methods) {
            ClientCmdMethod previous = cmdOwners.putIfAbsent(method.cmd, method);
            if (previous != null) {
                throw new IllegalStateException(method.serviceOwnerFullClassName + " 存在重复的客户端协议号: cmd="
                        + method.cmd + ", previous=" + previous.methodDisplayName()
                        + ", current=" + method.methodDisplayName());
            }
        }
    }

    private ClientCmdMethod parseMethod(ExecutableElement method,
                                        TypeElement sessionType,
                                        TypeElement protoMessageType) {
        TypeElement ownerType = (TypeElement) method.getEnclosingElement();
        TypeElement serviceOwner = resolveServiceOwner(ownerType);
        ClientCmd clientCmd = method.getAnnotation(ClientCmd.class);
        if (clientCmd == null) {
            throw new IllegalStateException(ownerType.getQualifiedName() + "#" + method.getSimpleName()
                    + " 找不到 @ClientCmd 注解");
        }
        int cmd = clientCmd.value();
        ActorType actorType = clientCmd.actorType();
        if (cmd <= 0) {
            throw new IllegalStateException(ownerType.getQualifiedName() + "#" + method.getSimpleName()
                    + " 的 @ClientCmd value 必须大于 0");
        }
        if (!method.getModifiers().contains(Modifier.PUBLIC) || method.getModifiers().contains(Modifier.STATIC)) {
            throw new IllegalStateException(ownerType.getQualifiedName() + "#" + method.getSimpleName()
                    + " 必须是 public 且非 static");
        }
        if (method.getReturnType().getKind() != TypeKind.VOID) {
            throw new IllegalStateException(ownerType.getQualifiedName() + "#" + method.getSimpleName()
                    + " 返回值必须是 void");
        }

        List<? extends VariableElement> parameters = method.getParameters();
        if (parameters.size() != 2) {
            throw new IllegalStateException(ownerType.getQualifiedName() + "#" + method.getSimpleName()
                    + " 参数必须固定为 (ClientSessionRef session, ProtoMessage req)");
        }

        VariableElement sessionParam = parameters.get(0);
        if (!typeUtils.isSameType(sessionParam.asType(), sessionType.asType())) {
            throw new IllegalStateException(ownerType.getQualifiedName() + "#" + method.getSimpleName()
                    + " 第一个参数必须是 " + CLIENT_SESSION_REF_CLASS_NAME);
        }

        VariableElement requestParam = parameters.get(1);
        TypeMirror requestType = requestParam.asType();
        if (!typeUtils.isSubtype(typeUtils.erasure(requestType), typeUtils.erasure(protoMessageType.asType()))) {
            throw new IllegalStateException(ownerType.getQualifiedName() + "#" + method.getSimpleName()
                    + " 第二个参数必须是 protobuf message 类型");
        }

        String requestTypeName = requestType.toString();
        Element requestTypeElement = typeUtils.asElement(requestType);
        String requestPackageName = elementUtils.getPackageOf(requestTypeElement).getQualifiedName().toString();
        String requestClassName = requestTypeName.substring(requestTypeName.lastIndexOf('.') + 1);
        return new ClientCmdMethod(
                elementUtils.getPackageOf(serviceOwner).getQualifiedName().toString(),
                serviceOwner.getSimpleName().toString(),
                serviceOwner.getQualifiedName().toString(),
                elementUtils.getPackageOf(ownerType).getQualifiedName().toString(),
                ownerType.getSimpleName().toString(),
                ownerType.getQualifiedName().toString(),
                method.getSimpleName().toString(),
                cmd,
                resolveCmdExpr(cmd),
                actorType,
                requestTypeName,
                requestPackageName,
                requestClassName,
                method
        );
    }

    private String resolveCmdExpr(int cmd) {
        return String.valueOf(cmd);
    }

    private List<String> buildTargetFieldLines(List<ClientCmdMethod> methods) {
        Map<String, String> fieldByTarget = new LinkedHashMap<>();
        List<String> fieldLines = new ArrayList<>();
        Set<String> usedFieldNames = new HashSet<>();
        for (ClientCmdMethod method : methods) {
            if (method.isServiceOwnerTarget() || fieldByTarget.containsKey(method.targetFullClassName)) {
                continue;
            }
            String fieldName = createFieldName(method.targetClassName, usedFieldNames);
            fieldByTarget.put(method.targetFullClassName, fieldName);
            method.fieldName = fieldName;
            fieldLines.add("    private final " + method.targetClassName + " " + fieldName + " = new "
                    + method.targetClassName + "();\n");
        }
        for (ClientCmdMethod method : methods) {
            if (!method.isServiceOwnerTarget()) {
                method.fieldName = fieldByTarget.get(method.targetFullClassName);
            }
        }
        return fieldLines;
    }

    private String createFieldName(String className, Set<String> usedFieldNames) {
        String baseName = Character.toLowerCase(className.charAt(0)) + className.substring(1);
        String fieldName = baseName;
        int suffix = 2;
        while (!usedFieldNames.add(fieldName)) {
            fieldName = baseName + suffix++;
        }
        return fieldName;
    }

    private boolean hasNonOwnerTarget(List<ClientCmdMethod> methods) {
        for (ClientCmdMethod method : methods) {
            if (!method.isServiceOwnerTarget()) {
                return true;
            }
        }
        return false;
    }

    private TypeElement requireType(String className) {
        TypeElement type = elementUtils.getTypeElement(className);
        if (type == null) {
            throw new IllegalStateException("找不到类型: " + className);
        }
        return type;
    }

    private TypeElement resolveServiceOwner(TypeElement ownerType) {
        return serviceOwnerResolver.resolve(ownerType);
    }

    private Element[] collectOriginatingElements(List<ClientCmdMethod> methods) {
        LinkedHashSet<Element> originatingElements = new LinkedHashSet<>();
        for (ClientCmdMethod method : methods) {
            originatingElements.add(method.sourceElement);
        }
        return originatingElements.toArray(Element[]::new);
    }

    private void writeJavaSource(String fullClassName, String content, Element... originatingElements) throws Exception {
        JavaFileObject sourceFile = filer.createSourceFile(fullClassName, originatingElements);
        try (Writer writer = sourceFile.openWriter()) {
            writer.write(content);
        }
    }

    private static final class ClientCmdMethod {
        private final String serviceOwnerPackageName;
        private final String serviceOwnerClassName;
        private final String serviceOwnerFullClassName;
        private final String targetPackageName;
        private final String targetClassName;
        private final String targetFullClassName;
        private final String methodName;
        private final int cmd;
        private final String cmdExpr;
        private final ActorType actorType;
        private final String requestTypeName;
        private final String requestPackageName;
        private final String requestClassName;
        private final ExecutableElement sourceElement;
        private String fieldName;

        private ClientCmdMethod(String serviceOwnerPackageName,
                                String serviceOwnerClassName,
                                String serviceOwnerFullClassName,
                                String targetPackageName,
                                String targetClassName,
                                 String targetFullClassName,
                                 String methodName,
                                 int cmd,
                                 String cmdExpr,
                                 ActorType actorType,
                                 String requestTypeName,
                                 String requestPackageName,
                                 String requestClassName,
                                 ExecutableElement sourceElement) {
            this.serviceOwnerPackageName = serviceOwnerPackageName;
            this.serviceOwnerClassName = serviceOwnerClassName;
            this.serviceOwnerFullClassName = serviceOwnerFullClassName;
            this.targetPackageName = targetPackageName;
            this.targetClassName = targetClassName;
            this.targetFullClassName = targetFullClassName;
            this.methodName = methodName;
            this.cmd = cmd;
            this.cmdExpr = cmdExpr;
            this.actorType = actorType;
            this.requestTypeName = requestTypeName;
            this.requestPackageName = requestPackageName;
            this.requestClassName = requestClassName;
            this.sourceElement = sourceElement;
        }

        private boolean isServiceOwnerTarget() {
            return serviceOwnerFullClassName.equals(targetFullClassName);
        }

        private String dispatchTargetExpr() {
            return isServiceOwnerTarget() ? "owner()" : fieldName;
        }

        private String targetTypeName() {
            return targetPackageName + "." + targetClassName;
        }

        private String methodDisplayName() {
            return targetFullClassName + "#" + methodName;
        }
    }
}
