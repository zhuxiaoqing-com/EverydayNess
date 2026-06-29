package org.evd.game.gencode;

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * 按项目约定解析注解方法所属的宿主 Service。
 * 规则：
 * 1. 若声明类本身就是 Service，直接返回声明类；
 * 2. 否则从包名自右向左查找以 Service 结尾的包段，并解析同名 Service 类。
 */
public final class ServiceOwnerResolver {
    private static final String SERVICE_CLASS_NAME = "org.evd.game.runtime.Service";

    private final Elements elementUtils;
    private final Types typeUtils;
    private final TypeElement serviceBaseType;

    public ServiceOwnerResolver(Elements elementUtils, Types typeUtils) {
        this.elementUtils = elementUtils;
        this.typeUtils = typeUtils;
        this.serviceBaseType = requireServiceBaseType();
    }

    public TypeElement resolve(TypeElement declaringType) {
        if (isServiceType(declaringType)) {
            return declaringType;
        }
        String packageName = getPackageName(declaringType);
        String[] segments = packageName.split("\\.");
        for (int i = segments.length - 1; i >= 0; i--) {
            String segment = segments[i];
            if (!segment.endsWith("Service")) {
                continue;
            }
            String candidatePackage = joinSegments(segments, i + 1);
            String candidateClassName = candidatePackage + "." + segment;
            TypeElement candidateType = elementUtils.getTypeElement(candidateClassName);
            if (candidateType != null && isServiceType(candidateType)) {
                return candidateType;
            }
        }
        throw new IllegalStateException(declaringType.getQualifiedName() + " 找不到宿主 Service。"
                + " 当前规则要求声明类本身是 Service，或其包路径中存在同名 XxxService 宿主类。");
    }

    public boolean isServiceType(TypeElement typeElement) {
        return typeUtils.isSubtype(typeElement.asType(), serviceBaseType.asType());
    }

    private TypeElement requireServiceBaseType() {
        TypeElement typeElement = elementUtils.getTypeElement(SERVICE_CLASS_NAME);
        if (typeElement == null) {
            throw new IllegalStateException("找不到类型: " + SERVICE_CLASS_NAME);
        }
        return typeElement;
    }

    private String getPackageName(TypeElement typeElement) {
        PackageElement packageElement = elementUtils.getPackageOf(typeElement);
        if (packageElement == null) {
            throw new IllegalStateException("找不到包信息: " + typeElement.getQualifiedName());
        }
        return packageElement.getQualifiedName().toString();
    }

    private String joinSegments(String[] segments, int endExclusive) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < endExclusive; i++) {
            if (i > 0) {
                builder.append('.');
            }
            builder.append(segments[i]);
        }
        return builder.toString();
    }
}
