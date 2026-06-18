package org.evd.game.gencode.struct;

import org.evd.game.annotation.RpcActorType;
import org.evd.game.annotation.RpcRoute;
import org.evd.game.gencode.AptUtils;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

public class MethodStruct<T> {
    /** 被注解标记的方法元素本身 */
    private Element element;
    /** 方法上对应的注解类型 */
    private Class<T> annotationClass;
    /** 该方法所在类的包名 */
    public String packageName;
    /** 该方法所在类的完整类名，包含包名 */
    public String fullClassName;
    /** 该方法所在类的简单类名，不包含包名 */
    public String className;
    /** 最终承载这个 RPC 方法的宿主 Service 完整类名 */
    public String ownerFullClassName;
    /** 最终承载这个 RPC 方法的宿主 Service 简单类名 */
    public String ownerClassName;
    /** 方法名 */
    public String methodName;
    /** RPC 分发时使用的方法编号，对应生成代码里的 EnumCall 值 */
    public int methodKey;
    /** 代理路由类型 */
    public RpcRoute rpcRoute;
    /** LOCATION 路由下的固定 actor 类型；NONE 表示调用方显式传 ActorId */
    public RpcActorType rpcActorType = RpcActorType.NONE;

    /** 返回值基础类型名，例如 int / String / void */
    public String returnType;
    /** 返回值包装类型名，例如 Integer / String / Void */
    public String returnTypeWrapper;
    /** 方法参数列表 */
    public ParamStruct[] params;


    public MethodStruct(Element element,
                        Class<T> annotationClass,
                        String packageName,
                        String classFullName,
                        String className,
                        String methodName,
                        String returnType,
                        ParamStruct[] params)
    {
        this.element = element;
        this.annotationClass = annotationClass;
        this.packageName = packageName;
        this.fullClassName = classFullName;
        this.className = className;
        this.methodName = methodName;
        this.returnType = AptUtils.typeToBase(returnType);
        this.returnTypeWrapper = AptUtils.typeToWrapper(returnType);
        this.params = params;
    }

    public String toParamNames(){
        StringBuilder nameParams = new StringBuilder();
        for (int i=0; i<params.length; ++i) {
            ParamStruct paramStruct = params[i];
            nameParams.append(paramStruct.paramName);
            if (i < params.length - 1){
                nameParams.append(", ");
            }
        }
        return nameParams.toString();
    }

    public String toParamTypesWitchReturn(){
        StringBuilder typeParams = new StringBuilder();
        if (returnType.equals("void")){
            if (params.length > 0){
                typeParams.append("<");
            }
            for (int i=0; i<params.length; ++i){
                ParamStruct paramStruct = params[i];
                typeParams.append(AptUtils.shortTypeName(paramStruct.paramTypeWrapper));
                if (i < params.length - 1){
                    typeParams.append(", ");
                }
            }
            if (params.length > 0){
                typeParams.append(">");
            }
        }else{
            typeParams.append("<");
            typeParams.append(AptUtils.shortTypeName(returnTypeWrapper));
            for (int i=0; i<params.length; ++i){
                ParamStruct paramStruct = params[i];
                typeParams.append(", ");
                typeParams.append(AptUtils.shortTypeName(paramStruct.paramTypeWrapper));
            }
            typeParams.append(">");
        }
        return typeParams.toString();
    }

    public String toParamTypeAndTypes(){
        StringBuilder formalParams = new StringBuilder();
        for (int i=0; i<params.length; ++i) {
            ParamStruct paramStruct = params[i];
            formalParams.append(AptUtils.shortTypeName(paramStruct.paramType))
                    .append(" ")
                    .append(paramStruct.paramName);
            if (i < params.length - 1){
                formalParams.append(", ");
            }
        }
        return formalParams.toString();
    }

    public String getDisplayReturnType() {
        return AptUtils.shortTypeName(returnType);
    }


    @Override
    public String toString() {
        StringBuilder sbf = new StringBuilder();
        sbf.append("package ").append(packageName).append("\n");
        sbf.append("\tclass ").append(fullClassName).append("{").append("\n");
        sbf.append("\t\t").append(returnType).append(" ").append(methodName).append("(");
        for (int i = 0; i < params.length; i++) {
            sbf.append(params[i].toString());
            if (i < params.length - 1) {
                sbf.append(", ");
            }
        }
        sbf.append(")").append("\n");
        sbf.append("\t}");
        return sbf.toString();
    }

    public TypeElement getTypeElement() {
        return (TypeElement)element.getEnclosingElement();
    }

    public ExecutableElement getExecutableElement() {
        return (ExecutableElement) element;
    }
}
