package org.evd.game.gencode.rpc;

import com.google.auto.service.AutoService;
import org.evd.game.annotation.Actor;
import org.evd.game.annotation.Rpc;
import org.evd.game.gencode.AptUtils;
import org.evd.game.gencode.GenConst;
import org.evd.game.gencode.ProcessorBase;
import org.evd.game.gencode.struct.MethodStruct;
import org.evd.game.gencode.struct.ParamStruct;
import org.evd.game.gencode.struct.StructFactory;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@AutoService(Processor.class)
public class RpcProcessor extends ProcessorBase {

    private final static String TEMPLATE_RPC_IMP = "RpcImp.ftl";
    private final static String TEMPLATE_RPC_PROXY = "RpcProxy.ftl";

    private List<MethodStruct<Rpc>> structList = new ArrayList<>();
    private Map<String, List<MethodStruct<Rpc>>> classMap = new HashMap<>();

    @Override
    protected Set<String> supportAnnotation() {
        return Collections.singleton(Rpc.class.getCanonicalName());
    }

    @Override
    protected void init() {
    }

    @Override
    protected void gen(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        println("");
        println("开始执行Rpc Processor");

        TypeElement ownerType = resolveServiceOwner(roundEnv.getElementsAnnotatedWith(Actor.class));

        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Rpc.class);
        if (elements == null || elements.isEmpty()) {
            return;
        }

        structList = StructFactory.convertMethod(elementUtils, elements, Rpc.class);
        structList.sort(Comparator
                .comparing((MethodStruct<Rpc> m) -> m.fullClassName)
                .thenComparing(m -> m.methodName)
                .thenComparing(m -> Arrays.stream(m.params)
                        .map(p -> p.paramType)
                        .collect(Collectors.joining(","))));

        for (MethodStruct<Rpc> method : structList) {
            println(method.toString());
        }

        List<MethodStruct<Rpc>> ownerMethods = bindOwner(ownerType);

        Map<String, Object> rpcImpRootMap = getRootMap(ownerMethods, ownerType, ownerType.getQualifiedName().toString());
        genRpcImp(rpcImpRootMap, ownerType.getQualifiedName().toString());

        classMap.forEach((classFullName, methods) -> {
            Map<String, Object> rootMap = getRootMap(methods, ownerType, classFullName);
            genRpcProxy(rootMap, classFullName);
        });
    }

    private List<MethodStruct<Rpc>> bindOwner(TypeElement ownerType) {
        classMap.clear();

        String ownerFullClassName = ownerType.getQualifiedName().toString();
        String ownerClassName = ownerType.getSimpleName().toString();
        for (MethodStruct<Rpc> method : structList) {
            method.ownerFullClassName = ownerFullClassName;
            method.ownerClassName = ownerClassName;
            classMap.computeIfAbsent(method.fullClassName, k -> new ArrayList<>()).add(method);
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

    private Map<String, Object> getRootMap(List<MethodStruct<Rpc>> methods, TypeElement ownerType, String generatedClassFullName) {
        MethodStruct<Rpc> struct = methods.getFirst();
        int splitIndex = generatedClassFullName.lastIndexOf(".");
        String generatedPackageName = generatedClassFullName.substring(0, splitIndex);
        String generatedClassName = generatedClassFullName.substring(splitIndex + 1);

        Map<String, Object> dataModel = new HashMap<>();
        Set<String> importPackages = new LinkedHashSet<>();
        List<String> importsModel = new ArrayList<>();
        List<Map<String, Object>> methodsModel = new ArrayList<>();
        List<Map<String, Object>> actorFieldsModel = new ArrayList<>();
        Set<String> actorFieldNames = new HashSet<>();

        dataModel.put("packageName", generatedPackageName);
        dataModel.put("commonPackageName", "org.evd.game.common.proxy");
        dataModel.put("className", generatedClassName);
        dataModel.put("fullClassName", generatedClassFullName);
        dataModel.put("ownerClassName", struct.ownerClassName);
        dataModel.put("ownerFullClassName", struct.ownerFullClassName);
        dataModel.put("importPackages", importsModel);
        dataModel.put("methods", methodsModel);
        dataModel.put("actorFields", actorFieldsModel);

        Actor serviceAnnotation = ownerType.getAnnotation(Actor.class);
        if (serviceAnnotation == null) {
            println(struct.className + "don't have @ServiceClass annotation");
            return dataModel;
        }
        dataModel.put("singleton", serviceAnnotation.single());

        for (MethodStruct<Rpc> method : methods) {
            collectMethodImports(importPackages, generatedPackageName, method);

            Map<String, Object> methodModel = new HashMap<>();
            methodsModel.add(methodModel);

            AptUtils.StringExt enumCall = new AptUtils.StringExt()
                    .appendJoin("ENUM", "_")
                    .appendJoin(method.className.toUpperCase(), "_")
                    .appendJoin(method.returnType.toUpperCase(), "_")
                    .append(method.methodName.toUpperCase());
            for (ParamStruct paramStruct : method.params) {
                enumCall.append("_");
                enumCall.append(paramStruct.paramType.toUpperCase());
            }

            String enumCallStr = toEnumToken(enumCall.toString());

            methodModel.put("enumCall", enumCallStr);
            methodModel.put("methodKey", method.methodKey);
            methodModel.put("paramSize", method.params.length);
            methodModel.put("methodName", method.methodName);
            methodModel.put("targetClassName", method.className);
            methodModel.put("targetFieldName", getActorFieldName(method.className));
            methodModel.put("targetIsOwner", method.fullClassName.equals(method.ownerFullClassName));

            String func = method.returnType.equals("void") ? "Function" : "ReturnFunction";
            methodModel.put("func", func);
            methodModel.put("typeParams", method.toParamTypesWitchReturn());

            methodModel.put("returnType", method.returnType);
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
            }
        }
        importsModel.addAll(importPackages);
        return dataModel;
    }

    private void genRpcImp(Map<String, Object> rootMap, String classFullName) {
        println("classFullName: " + classFullName);

        int splitIndex = classFullName.lastIndexOf(".");
        String packageName = classFullName.substring(0, splitIndex);
        println("packageName: " + packageName);
        String className = classFullName.substring(splitIndex + 1);
        println("className: " + className);
        String targetPath = getGenPath(packageName, className);
        String javaFileName = className + "Impl.java";

        try {
            AptUtils.freeMarker(GenConst.TEMPLATE_DIR, TEMPLATE_RPC_IMP, rootMap, targetPath, javaFileName);
            println("generate success [" + javaFileName + "]");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void genRpcProxy(Map<String, Object> rootMap, String classFullName) {
        int splitIndex = classFullName.lastIndexOf(".");
        String className = classFullName.substring(splitIndex + 1);
        String javaFileName = className + "Proxy.java";

        String[] array = classFullName.split("\\.");
        AptUtils.StringExt targetPath = new AptUtils.StringExt(GenConst.ROOT_PROJECT_PATH);
        targetPath.appendJoin("common");
        targetPath.appendJoin("src");
        targetPath.appendJoin("gen");
        targetPath.appendJoin("java");
        targetPath.appendJoin(array[0]);
        targetPath.appendJoin(array[1]);
        targetPath.appendJoin(array[2]);
        targetPath.appendJoin("common");
        targetPath.appendJoin("proxy");

        try {
            AptUtils.freeMarker(GenConst.TEMPLATE_DIR, TEMPLATE_RPC_PROXY, rootMap, targetPath.toString(), javaFileName);
            println("generate success [" + javaFileName + "]");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TypeElement resolveServiceOwner(Set<? extends Element> actorElements) {
        if (actorElements.isEmpty()) {
            throw new IllegalStateException("当前 RoundEnvironment 找不到 @Actor Service 宿主");
        }
        if (actorElements.size() > 1) {
            throw new IllegalStateException("一个 RoundEnvironment 只能有一个 @Actor Service 宿主: " + actorElements);
        }

        Element actorElement = actorElements.iterator().next();
        if (!(actorElement instanceof TypeElement typeElement)) {
            throw new IllegalStateException("@Actor 目标不是 TypeElement: " + actorElement);
        }
        return requireServiceOwner(typeElement);
    }

    private TypeElement requireServiceOwner(TypeElement typeElement) {
        TypeElement serviceElement = elementUtils.getTypeElement("org.evd.game.runtime.Service");
        if (!typeUtils.isSubtype(typeElement.asType(), serviceElement.asType())) {
            throw new IllegalStateException(typeElement.getQualifiedName() + " 标了 @Actor，但不是 Service 子类");
        }
        return typeElement;
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

    private String getActorFieldName(String className) {
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }
}
