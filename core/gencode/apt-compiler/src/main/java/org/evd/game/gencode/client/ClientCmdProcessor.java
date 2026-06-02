package org.evd.game.gencode.client;

import com.google.auto.service.AutoService;
import org.evd.game.annotation.ClientCmd;
import org.evd.game.gencode.AptUtils;
import org.evd.game.gencode.ProcessorBase;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final String MSG_ID_CLASS_NAME = "org.evd.game.common.proto.MsgId";
    private static final String PROTO_MESSAGE_CLASS_NAME = "com.google.protobuf.MessageLite";
    private static final String SERVICE_CLASS_NAME = "org.evd.game.runtime.Service";

    private final Set<String> generatedClasses = new HashSet<>();

    @Override
    protected Set<String> supportAnnotation() {
        return Collections.singleton(ClientCmd.class.getCanonicalName());
    }

    @Override
    protected void init() {
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
        TypeElement serviceType = requireType(SERVICE_CLASS_NAME);

        Map<String, List<ClientCmdMethod>> classMethods = new LinkedHashMap<>();
        for (Element element : elements) {
            if (!(element instanceof ExecutableElement executableElement)) {
                throw new IllegalStateException("@ClientCmd 只能标记在方法上: " + element);
            }
            ClientCmdMethod method = parseMethod(executableElement, sessionType, protoMessageType, serviceType);
            classMethods.computeIfAbsent(method.ownerFullClassName, key -> new ArrayList<>()).add(method);
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
        String className = first.ownerClassName + "ClientCmdRegistry";
        String packageName = first.ownerPackageName;
        String fullClassName = packageName + "." + className;
        if (!generatedClasses.add(fullClassName)) {
            return;
        }

        try {
            writeJavaSource(packageName, className, buildRegistrySource(first.ownerClassName, className, packageName, methods));
            println("generate success [" + className + ".java]");
        } catch (Exception e) {
            throw new RuntimeException("生成客户端协议分发表失败: " + className, e);
        }
    }

    private void genRouteRegistry(List<ClientCmdMethod> methods) {
        ClientCmdMethod first = methods.getFirst();
        String className = first.ownerClassName + "ClientCmdRouteRegistry";
        String packageName = first.ownerPackageName;
        String fullClassName = packageName + "." + className;
        if (!generatedClasses.add(fullClassName)) {
            return;
        }

        try {
            writeJavaSource(packageName, className, buildRouteRegistrySource(className, methods));
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
        source.append("import ").append(MSG_ID_CLASS_NAME).append(";\n");
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
        source.append("    public ").append(className).append("(").append(ownerClassName).append(" owner) {\n");
        source.append("        super(owner);\n");
        source.append("    }\n\n");
        source.append("    @Override\n");
        source.append("    public void dispatch(ClientSessionRef session, int cmd, byte[] body) throws InvalidProtocolBufferException {\n");
        source.append("        switch (cmd) {\n");
        for (ClientCmdMethod method : methods) {
            source.append("            case ").append(method.cmdExpr).append(":\n");
            source.append("                owner().").append(method.methodName)
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
        source.append("package ").append(first.ownerPackageName).append(";\n\n");
        source.append("import ").append(MSG_ID_CLASS_NAME).append(";\n");
        source.append("import ").append(CLIENT_CMD_ROUTE_TABLE_CLASS_NAME).append(";\n\n");
        source.append("/**\n");
        source.append(" * 根据").append(first.ownerClassName).append("生成的客户端协议路由注册类\n");
        source.append(" */\n");
        source.append("public final class ").append(className).append(" {\n");
        source.append("    private ").append(className).append("() {\n");
        source.append("    }\n\n");
        source.append("    public static void register(ClientCmdRouteTable routeTable) {\n");
        for (ClientCmdMethod method : methods) {
            source.append("        routeTable.register(")
                    .append(method.cmdExpr)
                    .append(", \"")
                    .append(method.ownerFullClassName)
                    .append("\");\n");
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
        }
        return new ArrayList<>(imports);
    }

    private void checkDuplicateCmd(List<ClientCmdMethod> methods) {
        Map<Integer, String> cmdOwners = new LinkedHashMap<>();
        for (ClientCmdMethod method : methods) {
            String previous = cmdOwners.putIfAbsent(method.cmd, method.methodName);
            if (previous != null) {
                throw new IllegalStateException(method.ownerFullClassName + " 存在重复的客户端协议号: cmd="
                        + method.cmd + ", method=" + previous + "/" + method.methodName);
            }
        }
    }

    private ClientCmdMethod parseMethod(ExecutableElement method,
                                        TypeElement sessionType,
                                        TypeElement protoMessageType,
                                        TypeElement serviceType) {
        TypeElement ownerType = (TypeElement) method.getEnclosingElement();
        ClientCmd clientCmd = method.getAnnotation(ClientCmd.class);
        int cmd = clientCmd.value();
        if (cmd <= 0) {
            throw new IllegalStateException(ownerType.getQualifiedName() + "#" + method.getSimpleName()
                    + " 的 @ClientCmd value 必须大于 0");
        }
        if (!typeUtils.isSubtype(ownerType.asType(), serviceType.asType())) {
            throw new IllegalStateException(ownerType.getQualifiedName() + "#" + method.getSimpleName()
                    + " 所在类必须是 Service 子类");
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
                elementUtils.getPackageOf(ownerType).getQualifiedName().toString(),
                ownerType.getSimpleName().toString(),
                ownerType.getQualifiedName().toString(),
                method.getSimpleName().toString(),
                cmd,
                buildMsgIdExpr(requestClassName),
                requestTypeName,
                requestPackageName,
                requestClassName
        );
    }

    private String buildMsgIdExpr(String requestClassName) {
        return MSG_ID_CLASS_NAME.substring(MSG_ID_CLASS_NAME.lastIndexOf('.') + 1) + "."
                + toMsgIdEnumName(requestClassName) + "_VALUE";
    }

    private String toMsgIdEnumName(String requestClassName) {
        StringBuilder result = new StringBuilder();
        String[] parts = requestClassName.split("_");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                result.append('_');
            }
            result.append(toUpperSnakePart(parts[i]));
        }
        return result.toString();
    }

    private String toUpperSnakePart(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (i > 0 && Character.isUpperCase(ch)) {
                char prev = value.charAt(i - 1);
                if (Character.isLowerCase(prev)) {
                    result.append('_');
                }
            }
            result.append(Character.toUpperCase(ch));
        }
        return result.toString();
    }

    private TypeElement requireType(String className) {
        TypeElement type = elementUtils.getTypeElement(className);
        if (type == null) {
            throw new IllegalStateException("找不到类型: " + className);
        }
        return type;
    }

    private void writeJavaSource(String packageName, String className, String content) throws Exception {
        String targetPath = getGenPath(packageName, className);
        File dir = new File(targetPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        Path filePath = Path.of(targetPath, className + ".java");
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(filePath), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    private static final class ClientCmdMethod {
        private final String ownerPackageName;
        private final String ownerClassName;
        private final String ownerFullClassName;
        private final String methodName;
        private final int cmd;
        private final String cmdExpr;
        private final String requestTypeName;
        private final String requestPackageName;
        private final String requestClassName;

        private ClientCmdMethod(String ownerPackageName,
                                String ownerClassName,
                                 String ownerFullClassName,
                                 String methodName,
                                 int cmd,
                                 String cmdExpr,
                                 String requestTypeName,
                                 String requestPackageName,
                                 String requestClassName) {
            this.ownerPackageName = ownerPackageName;
            this.ownerClassName = ownerClassName;
            this.ownerFullClassName = ownerFullClassName;
            this.methodName = methodName;
            this.cmd = cmd;
            this.cmdExpr = cmdExpr;
            this.requestTypeName = requestTypeName;
            this.requestPackageName = requestPackageName;
            this.requestClassName = requestClassName;
        }
    }
}
