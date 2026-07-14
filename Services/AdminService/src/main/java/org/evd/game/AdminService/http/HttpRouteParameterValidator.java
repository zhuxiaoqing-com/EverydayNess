package org.evd.game.AdminService.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * 校验 HTTP Controller 方法的参数组合是否合法。
 */
final class HttpRouteParameterValidator {
    private HttpRouteParameterValidator() {
    }

    static void validate(Method method, RequestType requestType) {
        int normalParameterCount = 0;
        int complexParameterCount = 0;
        int customObjectParameterCount = 0;
        int frameworkParameterCount = 0;

        for (Parameter parameter : method.getParameters()) {
            Class<?> type = parameter.getType();
            if (isFrameworkParameter(type)) {
                frameworkParameterCount++;
                if (frameworkParameterCount > 1) {
                    throw invalid(method, "最多只能有一个 HttpRequest 或 FullHttpRequest 参数");
                }
                continue;
            }
            if (ChannelHandlerContext.class.isAssignableFrom(type)) {
                throw invalid(method, "不支持 ChannelHandlerContext 参数，请使用 HttpRequest");
            }

            normalParameterCount++;
            if (isScalarType(type)) {
                if (requestType == RequestType.POST_JSON) {
                    throw invalid(method, "POST_JSON 只能使用一个自定义对象参数");
                }
                continue;
            }
            if (List.class.isAssignableFrom(type)) {
                if (requestType == RequestType.POST_JSON) {
                    throw invalid(method, "POST_JSON 只能使用一个自定义对象参数");
                }
                validateList(method, parameter.getParameterizedType());
                continue;
            }
            if (Map.class.isAssignableFrom(type)) {
                if (requestType == RequestType.POST_JSON) {
                    throw invalid(method, "POST_JSON 只能使用一个自定义对象参数");
                }
                complexParameterCount++;
                validateMap(method, parameter.getParameterizedType());
                continue;
            }
            complexParameterCount++;
            customObjectParameterCount++;
        }

        if (customObjectParameterCount > 0 && requestType != RequestType.POST_JSON) {
            throw invalid(method, "复杂对象参数只能用于 POST_JSON");
        }
        if (requestType == RequestType.POST_JSON
                && (normalParameterCount != 1 || customObjectParameterCount != 1)) {
            throw invalid(method, "POST_JSON 只能使用一个自定义对象参数");
        }
        if (complexParameterCount > 1) {
            throw invalid(method, "只能有一个自定义对象或 Map 参数");
        }
        if (complexParameterCount == 1 && normalParameterCount > 1) {
            throw invalid(method, "自定义对象或 Map 不能和基础类型、List 参数同时使用");
        }
    }

    static boolean isFrameworkParameter(Class<?> type) {
        return HttpRequest.class.isAssignableFrom(type)
                || FullHttpRequest.class.isAssignableFrom(type);
    }

    static boolean isScalarType(Class<?> type) {
        return type.isPrimitive()
                || type == String.class
                || type == Boolean.class
                || type == Character.class
                || type == Byte.class
                || type == Short.class
                || type == Integer.class
                || type == Long.class
                || type == Float.class
                || type == Double.class;
    }

    private static void validateList(Method method, Type genericType) {
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            throw invalid(method, "List 参数必须声明泛型，例如 List<Long>");
        }
        Type elementType = parameterizedType.getActualTypeArguments()[0];
        if (!(elementType instanceof Class<?> elementClass) || !isScalarType(elementClass)) {
            throw invalid(method, "目前只支持 List<基础类型>");
        }
    }

    private static void validateMap(Method method, Type genericType) {
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return;
        }

        Type keyType = parameterizedType.getActualTypeArguments()[0];
        Type valueType = parameterizedType.getActualTypeArguments()[1];
        if (keyType != String.class) {
            throw invalid(method, "Map 参数的 key 必须是 String");
        }
        if (valueType == String.class || valueType == Object.class) {
            return;
        }
        if (valueType instanceof ParameterizedType listType
                && listType.getRawType() == List.class
                && listType.getActualTypeArguments()[0] == String.class) {
            return;
        }
        throw invalid(method, "只支持 Map<String, String>、Map<String, Object> 或 Map<String, List<String>>");
    }

    private static IllegalStateException invalid(Method method, String message) {
        return new IllegalStateException("HTTP 路由方法参数不合法: "
                + method.getDeclaringClass().getName() + "#" + method.getName()
                + ", " + message);
    }
}
