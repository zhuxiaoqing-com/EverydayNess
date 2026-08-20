package org.evd.game.gencode.rpc;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.core.ParseException;
import org.evd.game.annotation.Rpc;
import org.evd.game.annotation.RpcService;
import org.evd.game.gencode.AptUtils;
import org.evd.game.gencode.GenConst;
import org.evd.game.gencode.ServiceOwnerResolver;
import org.evd.game.gencode.struct.MethodStruct;
import org.evd.game.gencode.struct.ParamStruct;
import org.evd.game.gencode.struct.StructFactory;
import org.evd.game.annotation.ActorType;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.File;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class RpcSupport {
    static final String TEMPLATE_RPC_IMP = "RpcImp.ftl";
    static final String TEMPLATE_RPC_PROXY = "RpcProxy.ftl";
    private static final String COMMON_PROXY_PACKAGE = "org.evd.game.common.proxy";
    /** 是否为所有 RPC 生成 callXxx(...) 结果方法。 */
    private static final boolean GENERATE_RESULT_METHODS = true;
    /** 是否为非 void RPC 生成带 long timeoutMillis 的普通与 Result 重载。 */
    private static final boolean GENERATE_TIMEOUT_OVERLOADS = false;

    private final Elements elementUtils;
    private final Types typeUtils;
    private final ServiceOwnerResolver serviceOwnerResolver;
    private final boolean generateResultMethods;
    private final boolean generateTimeoutOverloads;

    RpcSupport(ProcessingEnvironment processingEnv) {
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();
        this.serviceOwnerResolver = new ServiceOwnerResolver(elementUtils, typeUtils);
        this.generateResultMethods = GENERATE_RESULT_METHODS;
        this.generateTimeoutOverloads = GENERATE_TIMEOUT_OVERLOADS;
    }

    List<MethodStruct<Rpc>> buildRpcMethodStructs(RoundEnvironment roundEnv) {
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Rpc.class);
        if (elements == null || elements.isEmpty()) {
            return Collections.emptyList();
        }

        List<MethodStruct<Rpc>> structList = StructFactory.convertMethod(elementUtils, elements, Rpc.class);
        for (MethodStruct<Rpc> method : structList) {
            TypeElement ownerType = resolveServiceOwner(method);
            initRpcMetadata(method, ownerType);
            bindMethodOwner(method, ownerType);
        }
        structList.sort(Comparator
                .comparing((MethodStruct<Rpc> m) -> m.fullClassName)
                .thenComparing(m -> m.methodName)
                .thenComparing(m -> Arrays.stream(m.params)
                        .map(p -> p.paramType)
                        .collect(Collectors.joining(","))));
        return structList;
    }

    Map<String, List<MethodStruct<Rpc>>> groupRpcMethodsByClass(List<MethodStruct<Rpc>> structList) {
        Map<String, List<MethodStruct<Rpc>>> classMap = new LinkedHashMap<>();
        for (MethodStruct<Rpc> method : structList) {
            classMap.computeIfAbsent(method.fullClassName, key -> new ArrayList<>()).add(method);
        }
        return classMap;
    }

    RpcGenerationContext buildContext(RoundEnvironment roundEnv) {
        List<MethodStruct<Rpc>> structList = buildRpcMethodStructs(roundEnv);
        if (structList.isEmpty()) {
            return null;
        }
        TypeElement ownerType = resolveSingleServiceOwner(structList);
        List<MethodStruct<Rpc>> ownerMethods = bindOwner(ownerType, structList);
        Map<String, List<MethodStruct<Rpc>>> classMap = groupRpcMethodsByClass(ownerMethods);
        return new RpcGenerationContext(ownerType, structList, ownerMethods, classMap);
    }

    Map<String, Object> buildProxyRootMap(List<MethodStruct<Rpc>> methods) {
        MethodStruct<Rpc> struct = methods.getFirst();
        String generatedClassFullName = buildProxyClassFullName(struct.ownerClassName, struct.className);
        int splitIndex = generatedClassFullName.lastIndexOf(".");
        String generatedPackageName = generatedClassFullName.substring(0, splitIndex);
        String generatedClassName = generatedClassFullName.substring(splitIndex + 1);

        Map<String, Object> dataModel = new HashMap<>();
        Set<String> importPackages = new LinkedHashSet<>();
        List<String> importsModel = new ArrayList<>();
        List<Map<String, Object>> methodsModel = new ArrayList<>();
        boolean needsCallPointImport = false;
        boolean needsServiceImport = false;
        boolean needsLocationImport = false;
        boolean needsActorIdImport = false;
        boolean needsActorTypeImport = false;
        ProxyInterfaceMetadata proxyInterfaceMetadata = resolveProxyInterface(struct);

        dataModel.put("packageName", generatedPackageName);
        dataModel.put("commonPackageName", generatedPackageName);
        dataModel.put("className", struct.className);
        dataModel.put("generatedClassName", generatedClassName);
        dataModel.put("fullClassName", struct.fullClassName);
        dataModel.put("importPackages", importsModel);
        dataModel.put("methods", methodsModel);
        dataModel.put("implementsProxyInterface", proxyInterfaceMetadata != null);
        dataModel.put("proxyInterfaceSimpleName",
                proxyInterfaceMetadata == null ? "" : proxyInterfaceMetadata.simpleName);
        dataModel.put("generateResultMethods", generateResultMethods);
        dataModel.put("generateTimeoutOverloads", generateTimeoutOverloads);

        if (proxyInterfaceMetadata != null) {
            validateProxyInterface(proxyInterfaceMetadata.typeElement, methods);
            collectTypeImports(importPackages, generatedPackageName, proxyInterfaceMetadata.typeElement.asType());
        }
        validateResultMethodNameConflicts(methods);

        for (MethodStruct<Rpc> method : methods) {
            collectMethodImports(importPackages, generatedPackageName, method);
            boolean routeService = isServiceRoute(method);
            boolean routeLocation = isLocationRoute(method);
            String targetPrefix;
            if (routeService) {
                targetPrefix = "CallPoint remote";
                needsCallPointImport = true;
                needsServiceImport = true;
            } else {
                targetPrefix = "long actorUniqueId";
                needsServiceImport = true;
                needsActorTypeImport = true;
                needsActorIdImport = true;
                needsLocationImport = true;
            }
            if (routeLocation) {
                needsLocationImport = true;
            }

            Map<String, Object> methodModel = new HashMap<>();
            methodsModel.add(methodModel);

            methodModel.put("enumCall", buildEnumCallName(method));
            methodModel.put("methodKey", method.methodKey);
            methodModel.put("methodName", method.methodName);
            methodModel.put("resultMethodName", buildResultMethodName(method, routeService));
            methodModel.put("returnType", method.getDisplayReturnType());
            boolean isVoid = "void".equals(method.returnType);
            methodModel.put("returnTypeWrapper", isVoid ? "Void" : AptUtils.shortTypeName(method.returnTypeWrapper));
            methodModel.put("generateResultMethod", true);
            methodModel.put("isVoid", isVoid);
            methodModel.put("hasResult", !isVoid);
            String formalParams = method.toParamTypeAndTypes();
            String nameParams = method.toParamNames();
            String callTarget = routeService ? "remote" : "actorUniqueId";
            methodModel.put("formalParams", formalParams);
            methodModel.put("nameParams", nameParams);
            methodModel.put("resultFormalParams", targetPrefix
                    + (formalParams.isEmpty() ? "" : ", " + formalParams));
            methodModel.put("resultCallArgs", callTarget
                    + (nameParams.isEmpty() ? "" : ", " + nameParams));
            methodModel.put("targetPrefix", targetPrefix);
            methodModel.put("routeService", routeService);
            methodModel.put("routeLocation", routeLocation);
            methodModel.put("usesFixedActorType", routeLocation);
            methodModel.put("actorTypeName", method.rpcActorType.name());
        }

        dataModel.put("needsServiceImport", needsServiceImport);
        dataModel.put("needsCallPointImport", needsCallPointImport);
        dataModel.put("needsLocationImport", needsLocationImport);
        dataModel.put("needsActorIdImport", needsActorIdImport);
        dataModel.put("needsActorTypeImport", needsActorTypeImport);
        importsModel.addAll(importPackages);
        return dataModel;
    }

    private String buildProxyClassFullName(String ownerServiceClassName, String targetClassName) {
        return COMMON_PROXY_PACKAGE + "." + ownerServiceClassName + "." + targetClassName + "Proxy";
    }

    Map<String, Object> buildRootMap(List<MethodStruct<Rpc>> methods, String generatedClassFullName) {
        MethodStruct<Rpc> struct = methods.getFirst();
        int splitIndex = generatedClassFullName.lastIndexOf(".");
        String generatedPackageName = generatedClassFullName.substring(0, splitIndex);
        String generatedClassName = generatedClassFullName.substring(splitIndex + 1);

        Map<String, Object> dataModel = new HashMap<>();
        Set<String> importPackages = new LinkedHashSet<>();
        List<String> importsModel = new ArrayList<>();
        List<Map<String, Object>> methodsModel = new ArrayList<>();
        List<Map<String, Object>> actorFieldsModel = new ArrayList<>();
        List<Map<String, Object>> serviceTargetFieldsModel = new ArrayList<>();
        Set<String> actorFieldNames = new HashSet<>();
        Set<String> serviceTargetFieldNames = new HashSet<>();
        boolean needsCallPointImport = false;
        boolean needsLocationImport = false;
        boolean needsActorIdImport = false;
        boolean needsActorTypeImport = false;

        dataModel.put("packageName", generatedPackageName);
        dataModel.put("commonPackageName", "org.evd.game.common.proxy");
        dataModel.put("className", generatedClassName);
        dataModel.put("fullClassName", generatedClassFullName);
        dataModel.put("ownerClassName", struct.ownerClassName);
        dataModel.put("ownerFullClassName", struct.ownerFullClassName);
        dataModel.put("importPackages", importsModel);
        dataModel.put("methods", methodsModel);
        dataModel.put("actorFields", actorFieldsModel);
        dataModel.put("serviceTargetFields", serviceTargetFieldsModel);

        for (MethodStruct<Rpc> method : methods) {
            collectMethodImports(importPackages, generatedPackageName, method);
            if (!method.fullClassName.equals(method.ownerFullClassName)) {
                importPackages.add(method.fullClassName);
            }
            boolean routeService = isServiceRoute(method);
            boolean routeLocation = isLocationRoute(method);
            String targetPrefix;
            if (routeService) {
                targetPrefix = "CallPoint remote";
                needsCallPointImport = true;
            } else {
                targetPrefix = "long actorUniqueId";
                needsActorTypeImport = true;
                needsActorIdImport = true;
                needsLocationImport = true;
            }
            if (routeLocation) {
                needsLocationImport = true;
            }

            Map<String, Object> methodModel = new HashMap<>();
            methodsModel.add(methodModel);

            methodModel.put("enumCall", buildEnumCallName(method));
            methodModel.put("methodKey", method.methodKey);
            methodModel.put("paramSize", method.params.length);
            methodModel.put("methodName", method.methodName);
            methodModel.put("targetClassName", method.className);
            methodModel.put("targetFieldName", getActorFieldName(method.className));
            methodModel.put("targetIsOwner", method.fullClassName.equals(method.ownerFullClassName));
            methodModel.put("routeService", routeService);
            methodModel.put("routeLocation", routeLocation);
            methodModel.put("usesFixedActorType", routeLocation);
            methodModel.put("actorTypeName", method.rpcActorType.name());
            methodModel.put("targetPrefix", targetPrefix);

            String func = method.returnType.equals("void") ? "Function" : "ReturnFunction";
            methodModel.put("func", func);
            methodModel.put("typeParams", method.toParamTypesWitchReturn());
            methodModel.put("returnType", method.getDisplayReturnType());
            methodModel.put("formalParams", method.toParamTypeAndTypes());
            methodModel.put("nameParams", method.toParamNames());

            if (!method.fullClassName.equals(method.ownerFullClassName)) {
                String fieldName = getActorFieldName(method.className);
                if (actorFieldNames.add(fieldName)) {
                    Map<String, Object> actorFieldModel = new HashMap<>();
                    actorFieldModel.put("className", method.className);
                    actorFieldModel.put("fieldName", fieldName);
                    actorFieldsModel.add(actorFieldModel);
                }
                if (routeService && serviceTargetFieldNames.add(fieldName)) {
                    Map<String, Object> serviceTargetFieldModel = new HashMap<>();
                    serviceTargetFieldModel.put("className", method.className);
                    serviceTargetFieldModel.put("fieldName", fieldName);
                    serviceTargetFieldsModel.add(serviceTargetFieldModel);
                }
            }
        }

        dataModel.put("needsCallPointImport", needsCallPointImport);
        dataModel.put("needsLocationImport", needsLocationImport);
        dataModel.put("needsActorIdImport", needsActorIdImport);
        dataModel.put("needsActorTypeImport", needsActorTypeImport);
        importsModel.addAll(importPackages);
        return dataModel;
    }

    String renderTemplate(String templateName, Map<String, Object> rootMap) throws Exception {
        Configuration configuration = new Configuration();
        configuration.setDirectoryForTemplateLoading(new File(GenConst.TEMPLATE_DIR));
        configuration.setEncoding(Locale.getDefault(), "UTF-8");
        Template template;
        try {
            template = configuration.getTemplate(templateName, "UTF-8");
        } catch (ParseException parseException) {
            String parsedTemplateName = parseException.getTemplateName();
            throw new IllegalStateException(
                    "RPC FreeMarker 模板语法错误: template="
                            + (parsedTemplateName == null ? templateName : parsedTemplateName)
                            + ", line=" + parseException.getLineNumber()
                            + ", column=" + parseException.getColumnNumber()
                            + ", endLine=" + parseException.getEndLineNumber()
                            + ", endColumn=" + parseException.getEndColumnNumber());
        }
        StringWriter out = new StringWriter();
        template.process(rootMap, out);
        return out.toString();
    }

    private List<MethodStruct<Rpc>> bindOwner(TypeElement ownerType,
                                              List<MethodStruct<Rpc>> structList) {
        for (MethodStruct<Rpc> method : structList) {
            bindMethodOwner(method, ownerType);
        }

        List<MethodStruct<Rpc>> ownerMethods = new ArrayList<>(structList);
        ownerMethods.sort(Comparator
                .comparing((MethodStruct<Rpc> method) -> method.fullClassName.equals(method.ownerFullClassName) ? 0 : 1)
                .thenComparing(method -> method.fullClassName)
                .thenComparing(method -> method.methodName)
                .thenComparing(method -> Arrays.stream(method.params)
                        .map(p -> p.paramType)
                        .collect(Collectors.joining(","))));

        for (int i = 0; i < ownerMethods.size(); i++) {
            ownerMethods.get(i).methodKey = i;
        }
        return ownerMethods;
    }

    private TypeElement resolveSingleServiceOwner(List<MethodStruct<Rpc>> structList) {
        TypeElement ownerType = elementUtils.getTypeElement(structList.getFirst().ownerFullClassName);
        if (ownerType == null) {
            throw new IllegalStateException("找不到宿主 Service: " + structList.getFirst().ownerFullClassName);
        }
        for (MethodStruct<Rpc> method : structList) {
            if (!ownerType.getQualifiedName().contentEquals(method.ownerFullClassName)) {
                throw new IllegalStateException("当前编译单元的 @Rpc 方法解析到多个宿主 Service，无法合并生成: "
                        + ownerType.getQualifiedName() + " / " + method.ownerFullClassName);
            }
        }
        return ownerType;
    }

    private TypeElement resolveServiceOwner(MethodStruct<Rpc> method) {
        return serviceOwnerResolver.resolve(method.getTypeElement());
    }

    private void bindMethodOwner(MethodStruct<Rpc> method, TypeElement ownerType) {
        method.ownerFullClassName = ownerType.getQualifiedName().toString();
        method.ownerClassName = ownerType.getSimpleName().toString();
    }

    private void collectMethodImports(Set<String> importPackages, String generatedPackageName, MethodStruct<Rpc> method) {
        ExecutableElement executableElement = method.getExecutableElement();
        collectTypeImports(importPackages, generatedPackageName, executableElement.getReturnType());
        for (VariableElement parameter : executableElement.getParameters()) {
            collectTypeImports(importPackages, generatedPackageName, parameter.asType());
        }
    }

    private void collectTypeImports(Set<String> importPackages, String generatedPackageName, TypeMirror typeMirror) {
        if (typeMirror == null) {
            return;
        }
        if (typeMirror.getKind() == TypeKind.ARRAY) {
            collectTypeImports(importPackages, generatedPackageName, ((ArrayType) typeMirror).getComponentType());
            return;
        }
        if (typeMirror.getKind() != TypeKind.DECLARED) {
            return;
        }

        DeclaredType declaredType = (DeclaredType) typeMirror;
        Element element = declaredType.asElement();
        if (element instanceof TypeElement typeElement) {
            String qualifiedName = typeElement.getQualifiedName().toString();
            if (needImport(generatedPackageName, qualifiedName)) {
                importPackages.add(qualifiedName);
            }
        }
        for (TypeMirror typeArgument : declaredType.getTypeArguments()) {
            collectTypeImports(importPackages, generatedPackageName, typeArgument);
        }
    }

    private boolean needImport(String generatedPackageName, String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isEmpty()) {
            return false;
        }
        int lastDot = qualifiedName.lastIndexOf('.');
        if (lastDot < 0) {
            return false;
        }
        String packageName = qualifiedName.substring(0, lastDot);
        return !packageName.equals("java.lang") && !packageName.equals(generatedPackageName);
    }

    private String toEnumToken(String value) {
        return value.toUpperCase().replace('.', '_').replaceAll("[^A-Z0-9_]", "_");
    }

    private String buildEnumCallName(MethodStruct<Rpc> method) {
        return toEnumToken("ENUM_" + method.className + "_" + method.methodName + "_" + method.methodKey);
    }

    private void initRpcMetadata(MethodStruct<Rpc> method, TypeElement ownerType) {
        Rpc rpc = method.getExecutableElement().getAnnotation(Rpc.class);
        if (rpc == null) {
            throw new IllegalStateException("找不到 @Rpc 注解: " + method.fullClassName + "#" + method.methodName);
        }
        method.rpcActorType = rpc.actorType();
        validateRpcActorType(method, ownerType);
    }

    private void validateRpcActorType(MethodStruct<Rpc> method, TypeElement ownerType) {
        if (method.rpcActorType == ActorType.NONE) {
            return;
        }
        String actorOwnerServiceClassName = method.rpcActorType.getOwnerServiceClassName();
        if (actorOwnerServiceClassName == null || actorOwnerServiceClassName.isEmpty()) {
            throw new IllegalStateException("ActorType." + method.rpcActorType.name()
                    + " 缺少 ownerServiceClassName，无法生成 RPC: "
                    + method.fullClassName + "#" + method.methodName);
        }
        String ownerServiceClassName = ownerType.getSimpleName().toString();
        if (!actorOwnerServiceClassName.equals(ownerServiceClassName)) {
            throw new IllegalStateException("RPC actorType 归属的 Service 类名不匹配: "
                    + method.fullClassName + "#" + method.methodName
                    + " 使用了 ActorType." + method.rpcActorType.name()
                    + "，要求宿主服务类名是 " + actorOwnerServiceClassName
                    + "，实际是 " + ownerType.getSimpleName());
        }
    }

    private String getActorFieldName(String className) {
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    private ProxyInterfaceMetadata resolveProxyInterface(MethodStruct<Rpc> struct) {
        if (!struct.fullClassName.equals(struct.ownerFullClassName)) {
            return null;
        }
        TypeElement ownerType = elementUtils.getTypeElement(struct.ownerFullClassName);
        if (ownerType == null) {
            return null;
        }
        TypeElement proxyInterfaceType = resolveRpcServiceInterface(ownerType);
        if (proxyInterfaceType == null) {
            return null;
        }
        return new ProxyInterfaceMetadata(
                proxyInterfaceType.getQualifiedName().toString(),
                proxyInterfaceType.getSimpleName().toString(),
                proxyInterfaceType);
    }

    private TypeElement resolveRpcServiceInterface(TypeElement ownerType) {
        for (var annotationMirror : elementUtils.getAllAnnotationMirrors(ownerType)) {
            Element annotationElement = annotationMirror.getAnnotationType().asElement();
            if (!(annotationElement instanceof TypeElement annotationType)) {
                continue;
            }
            if (!annotationType.getQualifiedName().contentEquals(RpcService.class.getCanonicalName())) {
                continue;
            }
            for (var entry : elementUtils.getElementValuesWithDefaults(annotationMirror).entrySet()) {
                if (!entry.getKey().getSimpleName().contentEquals("value")) {
                    continue;
                }
                Object value = entry.getValue().getValue();
                if (!(value instanceof TypeMirror typeMirror)) {
                    throw new IllegalStateException("@RpcService value 不是合法类型: " + ownerType.getQualifiedName());
                }
                if (typeMirror.getKind() == TypeKind.VOID) {
                    return null;
                }
                Element interfaceElement = typeUtils.asElement(typeMirror);
                if (!(interfaceElement instanceof TypeElement interfaceType)) {
                    throw new IllegalStateException("@RpcService value 不是 TypeElement: " + ownerType.getQualifiedName());
                }
                if (interfaceType.getKind() != ElementKind.INTERFACE) {
                    throw new IllegalStateException("@RpcService 要求配置 interface，实际是: "
                            + interfaceType.getQualifiedName());
                }
                return interfaceType;
            }
        }
        return null;
    }

    private void validateProxyInterface(TypeElement proxyInterfaceType, List<MethodStruct<Rpc>> methods) {
        Set<String> generatedSignatures = collectGeneratedProxySignatures(methods);
        for (Element member : elementUtils.getAllMembers(proxyInterfaceType)) {
            if (!(member instanceof ExecutableElement executableElement)) {
                continue;
            }
            if (member.getKind() != ElementKind.METHOD) {
                continue;
            }
            Set<Modifier> modifiers = executableElement.getModifiers();
            if (modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.DEFAULT)) {
                continue;
            }
            if (!modifiers.contains(Modifier.ABSTRACT)) {
                continue;
            }
            String interfaceSignature = buildExecutableSignature(executableElement);
            if (!generatedSignatures.contains(interfaceSignature)) {
                throw new IllegalStateException("RPC 代理未覆盖接口方法: "
                        + proxyInterfaceType.getQualifiedName() + "#" + interfaceSignature);
            }
        }
    }

    private Set<String> collectGeneratedProxySignatures(List<MethodStruct<Rpc>> methods) {
        Set<String> signatures = new LinkedHashSet<>();
        for (MethodStruct<Rpc> method : methods) {
            signatures.add(buildGeneratedSignature(method, false));
            if (generateTimeoutOverloads && !"void".equals(method.returnType)) {
                signatures.add(buildGeneratedSignature(method, true));
            }
        }
        return signatures;
    }

    private void validateResultMethodNameConflicts(List<MethodStruct<Rpc>> methods) {
        if (!generateResultMethods) {
            return;
        }
        Set<String> regularMethods = new HashSet<>();
        for (MethodStruct<Rpc> method : methods) {
            regularMethods.add(buildGeneratedMethodKey(method.methodName, method, false));
            if (generateTimeoutOverloads && !"void".equals(method.returnType)) {
                regularMethods.add(buildGeneratedMethodKey(method.methodName, method, true));
            }
        }
        for (MethodStruct<Rpc> method : methods) {
            String resultMethodName = buildResultMethodName(method, isServiceRoute(method));
            if (regularMethods.contains(buildGeneratedMethodKey(resultMethodName, method, false))) {
                throw new IllegalStateException("RPC 结果代理方法命名冲突: " + resultMethodName);
            }
            if (generateTimeoutOverloads
                    && regularMethods.contains(buildGeneratedMethodKey(resultMethodName, method, true))) {
                throw new IllegalStateException("RPC 结果超时代理方法命名冲突: " + resultMethodName);
            }
        }
    }

    private String buildCallMethodName(String methodName) {
        return "call" + Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);
    }

    private String buildResultMethodName(MethodStruct<Rpc> method, boolean routeService) {
        if ("void".equals(method.returnType) && routeService) {
            return "send" + Character.toUpperCase(method.methodName.charAt(0)) + method.methodName.substring(1);
        }
        return buildCallMethodName(method.methodName);
    }


    private String buildGeneratedMethodKey(String methodName,
                                           MethodStruct<Rpc> method,
                                           boolean timeoutOverload) {
        List<String> paramTypes = new ArrayList<>();
        paramTypes.add(isServiceRoute(method) ? "org.evd.game.runtime.call.CallPoint" : "long");
        for (ParamStruct param : method.params) {
            paramTypes.add(param.paramType);
        }
        if (timeoutOverload) {
            paramTypes.add("long");
        }
        return methodName + "(" + String.join(",", paramTypes) + ")";
    }

    private String buildGeneratedSignature(MethodStruct<Rpc> method, boolean timeoutOverload) {
        List<String> paramTypes = new ArrayList<>();
        boolean routeService = isServiceRoute(method);
        if (routeService) {
            paramTypes.add("org.evd.game.runtime.call.CallPoint");
        } else {
            paramTypes.add("long");
        }
        for (ParamStruct param : method.params) {
            paramTypes.add(param.paramType);
        }
        if (timeoutOverload) {
            paramTypes.add("long");
        }
        return buildSignature(method.methodName, paramTypes, method.returnType);
    }

    private String buildExecutableSignature(ExecutableElement executableElement) {
        List<String> paramTypes = executableElement.getParameters().stream()
                .map(parameter -> parameter.asType().toString())
                .collect(Collectors.toList());
        return buildSignature(
                executableElement.getSimpleName().toString(),
                paramTypes,
                executableElement.getReturnType().toString());
    }

    private String buildSignature(String methodName, List<String> paramTypes, String returnType) {
        return methodName + "(" + String.join(",", paramTypes) + "):" + returnType;
    }

    private boolean isServiceRoute(MethodStruct<Rpc> method) {
        return method.rpcActorType == ActorType.NONE;
    }

    private boolean isLocationRoute(MethodStruct<Rpc> method) {
        return method.rpcActorType != ActorType.NONE;
    }
}

final class RpcGenerationContext {
    final TypeElement ownerType;
    final List<MethodStruct<Rpc>> structList;
    final List<MethodStruct<Rpc>> ownerMethods;
    final Map<String, List<MethodStruct<Rpc>>> classMap;

    RpcGenerationContext(TypeElement ownerType,
                         List<MethodStruct<Rpc>> structList,
                         List<MethodStruct<Rpc>> ownerMethods,
                         Map<String, List<MethodStruct<Rpc>>> classMap) {
        this.ownerType = ownerType;
        this.structList = structList;
        this.ownerMethods = ownerMethods;
        this.classMap = classMap;
    }
}

final class ProxyInterfaceMetadata {
    final String qualifiedName;
    final String simpleName;
    final TypeElement typeElement;

    ProxyInterfaceMetadata(String qualifiedName, String simpleName, TypeElement typeElement) {
        this.qualifiedName = qualifiedName;
        this.simpleName = simpleName;
        this.typeElement = typeElement;
    }
}
