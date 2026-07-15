package org.evd.game.AdminService.http;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;
import org.evd.game.runtime.Service;
import org.evd.game.runtime.support.LogCore;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 直接按路径分发到 AdminService controller。
 */
public final class AdminHttpServerInboundHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final AdminHttpRouteRegistry routeRegistry;
    private final Service service;

    public AdminHttpServerInboundHandler(AdminHttpRouteRegistry routeRegistry) {
        this(routeRegistry, null);
    }

    public AdminHttpServerInboundHandler(AdminHttpRouteRegistry routeRegistry, Service service) {
        this.routeRegistry = routeRegistry;
        this.service = service;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (!request.decoderResult().isSuccess()) {
            writeResponse(ctx, request, HttpResponseStatus.BAD_REQUEST, "bad request", "text/plain; charset=UTF-8");
            return;
        }
        if (request.method() != io.netty.handler.codec.http.HttpMethod.GET
                && request.method() != io.netty.handler.codec.http.HttpMethod.POST) {
            writeResponse(ctx, request, HttpResponseStatus.METHOD_NOT_ALLOWED, "only GET/POST supported", "text/plain; charset=UTF-8");
            return;
        }
        String routeKey = HttpRequestMappingParser.normalizePath(new QueryStringDecoder(request.uri()).path());
        try {
            HttpRouteDefinition routeDefinition = routeRegistry.routes().get(routeKey);
            if (routeDefinition == null) {
                LogCore.core.warn("service={} HTTP 路由不存在: method={}, path={}",
                        service.getId(), request.method(), routeKey);
                writeResponse(ctx, request, HttpResponseStatus.NOT_FOUND, "route not found: " + routeKey, "text/plain; charset=UTF-8");
                return;
            }
            HttpResponseStatus requestTypeError = validateRequestType(routeDefinition.requestType(), request);
            if (requestTypeError != null) {
                writeResponse(ctx, request, requestTypeError,
                        "请求类型不匹配: expected=" + routeDefinition.requestType(),
                        "text/plain; charset=UTF-8");
                return;
            }
            LogCore.core.info("service={} HTTP 请求: method={}, path={}, handler={}#{}",
                    service.getId(), request.method(), routeKey,
                    routeDefinition.controllerClassName(), routeDefinition.methodName());
            boolean keepAlive = HttpUtil.isKeepAlive(request);
            String requestMethod = request.method().name();
            Object[] args = buildArgs(ctx, routeDefinition.parameters(), request, routeDefinition.requestType());
            service.postCoroutine(() -> {
                try {
                    Object result = invokeRoute(routeDefinition, args);
                    if (result instanceof CompletionStage<?> stage) {
                        stage.whenComplete((value, error) -> {
                            if (error == null) {
                                writeSuccessResponse(ctx, keepAlive, value);
                                return;
                            }
                            LogCore.core.error("service={} HTTP 异步调用失败: method={}, path={}",
                                    service.getId(), requestMethod, routeKey, error);
                            writeResponse(ctx, keepAlive, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                                    errorMessage(error), "text/plain; charset=UTF-8");
                        });
                        return;
                    }
                    writeSuccessResponse(ctx, keepAlive, result);
                } catch (Throwable e) {
                    LogCore.core.error("service={} HTTP 调用失败: method={}, path={}",
                            service.getId(), requestMethod, routeKey, e);
                    HttpResponseStatus status = e instanceof IllegalArgumentException
                            ? HttpResponseStatus.BAD_REQUEST
                            : HttpResponseStatus.INTERNAL_SERVER_ERROR;
                    writeResponse(ctx, keepAlive, status, errorMessage(e), "text/plain; charset=UTF-8");
                }
            });

        } catch (Throwable e) {
            LogCore.core.error("service={} HTTP 调用失败: method={}, path={}",
                    service.getId(), request.method(), routeKey, e);
            HttpResponseStatus status = e instanceof IllegalArgumentException
                    ? HttpResponseStatus.BAD_REQUEST
                    : HttpResponseStatus.INTERNAL_SERVER_ERROR;
            writeResponse(ctx, request, status, errorMessage(e), "text/plain; charset=UTF-8");
        }
    }

    private Object invokeRoute(HttpRouteDefinition routeDefinition,
                               Object[] args) throws Throwable {
        Method method = routeDefinition.method();
        try {
            return method.invoke(routeDefinition.controller(), args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    private Object[] buildArgs(ChannelHandlerContext ctx,
                               List<HttpRouteParameter> parameters,
                               FullHttpRequest request,
                               RequestType requestType) {
        Object[] args = new Object[parameters.size()];
        HttpParameterValues requestParameters = HttpParameterValues.parse(request, requestType);
        boolean jsonRequest = requestType == RequestType.POST_JSON;
        JSONObject bodyJson = jsonRequest ? parseJsonBody(request) : null;
        HttpRequest httpRequest = null;
        int normalParameterCount = countNormalParameters(parameters);

        for (int i = 0; i < parameters.size(); i++) {
            HttpRouteParameter parameter = parameters.get(i);
            Class<?> parameterType = parameter.parameterType();
            if (HttpRequest.class.isAssignableFrom(parameterType)) {
                if (httpRequest == null) {
                    httpRequest = HttpRequest.from(service, ctx, request, requestParameters, requestType);
                }
                args[i] = httpRequest;
                continue;
            }
            String parameterName = parameter.name();
            if (isScalarType(parameterType)) {
                if (jsonRequest) {
                    throw new IllegalArgumentException("POST_JSON 只能使用一个自定义对象参数");
                }
                Object rawValue = requestParameters.first(parameterName);
                args[i] = convertScalar(rawValue, parameterType);
                continue;
            }

            if (List.class.isAssignableFrom(parameterType)) {
                if (jsonRequest) {
                    throw new IllegalArgumentException("POST_JSON 只能使用一个自定义对象参数");
                }
                args[i] = convertList(requestParameters.all(parameterName), parameter.genericType());
                continue;
            }

            if (Map.class.isAssignableFrom(parameterType)) {
                if (jsonRequest) {
                    throw new IllegalArgumentException("POST_JSON 只能使用一个自定义对象参数");
                }
                requireSingleNormalParameter(normalParameterCount, "Map");
                args[i] = convertMap(requestParameters, parameter.genericType());
                continue;
            }

            requireSingleNormalParameter(normalParameterCount, "复杂对象");
            if (!jsonRequest) {
                throw new IllegalArgumentException("复杂对象参数只能用于 POST_JSON");
            }
            args[i] = convertJsonValue(bodyJson, parameter.genericType());
        }
        return args;
    }

    private static int countNormalParameters(List<HttpRouteParameter> parameters) {
        int count = 0;
        for (HttpRouteParameter parameter : parameters) {
            Class<?> type = parameter.parameterType();
            if (!HttpRequest.class.isAssignableFrom(type)) {
                count++;
            }
        }
        return count;
    }

    private static void requireSingleNormalParameter(int count, String parameterDescription) {
        if (count != 1) {
            throw new IllegalArgumentException(parameterDescription
                    + "参数不能和其他普通参数同时使用，当前普通参数数量=" + count);
        }
    }

    private static boolean isScalarType(Class<?> type) {
        return HttpRouteParameterValidator.isScalarType(type);
    }

    private static Object convertScalar(Object rawValue, Class<?> targetType) {
        if (rawValue == null) {
            if (targetType.isPrimitive()) {
                throw new IllegalArgumentException("基础类型参数不能为空: " + targetType.getName());
            }
            return null;
        }
        if (targetType.isInstance(rawValue)) {
            return rawValue;
        }

        String value = String.valueOf(rawValue);
        if (targetType == String.class) {
            return value;
        }
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.valueOf(value);
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.valueOf(value);
        }
        if (targetType == short.class || targetType == Short.class) {
            return Short.valueOf(value);
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return Byte.valueOf(value);
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.valueOf(value);
        }
        if (targetType == float.class || targetType == Float.class) {
            return Float.valueOf(value);
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value) || "0".equals(value)) {
                return false;
            }
            throw new IllegalArgumentException("无法转换为 boolean: " + value);
        }
        if (targetType == char.class || targetType == Character.class) {
            if (value.length() != 1) {
                throw new IllegalArgumentException("无法转换为 char: " + value);
            }
            return value.charAt(0);
        }
        throw new IllegalArgumentException("不支持的 HTTP 参数类型: " + targetType.getName());
    }

    private static Object convertList(List<String> values, Type genericType) {
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            throw new IllegalArgumentException("List 参数必须声明泛型，例如 List<Long>");
        }
        Type elementType = parameterizedType.getActualTypeArguments()[0];
        if (!(elementType instanceof Class<?> elementClass) || !isScalarType(elementClass)) {
            throw new IllegalArgumentException("目前只支持 List<基础类型>: " + genericType);
        }

        List<Object> result = new ArrayList<>(values.size());
        for (String value : values) {
            result.add(convertScalar(value, elementClass));
        }
        return result;
    }

    private static Object convertMap(HttpParameterValues parameters, Type genericType) {
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return new LinkedHashMap<>(parameters.asObjectMap());
        }

        Type keyType = parameterizedType.getActualTypeArguments()[0];
        Type valueType = parameterizedType.getActualTypeArguments()[1];
        if (keyType != String.class) {
            throw new IllegalArgumentException("HTTP Map 参数的 key 必须是 String");
        }
        if (valueType == String.class) {
            LinkedHashMap<String, String> result = new LinkedHashMap<>();
            parameters.asMultiValueMap().forEach((name, values) ->
                    result.put(name, values.isEmpty() ? null : values.getFirst()));
            return result;
        }
        if (valueType == Object.class) {
            return new LinkedHashMap<>(parameters.asObjectMap());
        }
        if (valueType instanceof ParameterizedType listType
                && listType.getRawType() == List.class
                && listType.getActualTypeArguments()[0] == String.class) {
            return new LinkedHashMap<>(parameters.asMultiValueMap());
        }
        throw new IllegalArgumentException("只支持 Map<String, String>、Map<String, Object> 或 Map<String, List<String>>");
    }

    private static Object convertJsonValue(Object value, Type targetType) {
        if (value == null) {
            return null;
        }
        return JSON.parseObject(JSON.toJSONString(value), targetType);
    }

    private static JSONObject parseJsonBody(FullHttpRequest request) {
        String bodyText = readBodyText(request);
        if (bodyText.isBlank()) {
            return new JSONObject();
        }
        return JSON.parseObject(bodyText);
    }

    private static HttpResponseStatus validateRequestType(RequestType requestType,
                                                          FullHttpRequest request) {
        if (requestType == RequestType.GET) {
            return request.method() == io.netty.handler.codec.http.HttpMethod.GET
                    ? null
                    : HttpResponseStatus.METHOD_NOT_ALLOWED;
        }
        if (request.method() != io.netty.handler.codec.http.HttpMethod.POST) {
            return HttpResponseStatus.METHOD_NOT_ALLOWED;
        }
        if (requestType == RequestType.POST_FORM && !HttpParameterValues.isFormRequest(request)) {
            return HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE;
        }
        if (requestType == RequestType.POST_JSON && !HttpParameterValues.isJsonRequest(request)) {
            return HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE;
        }
        return null;
    }

    private static String errorMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static String readBodyText(FullHttpRequest request) {
        ByteBuf content = request.content();
        if (content == null || !content.isReadable()) {
            return "";
        }
        return content.toString(CharsetUtil.UTF_8);
    }

    private void writeSuccessResponse(ChannelHandlerContext ctx, boolean keepAlive, Object result) {
        if (result == null) {
            writeResponse(ctx, keepAlive, HttpResponseStatus.OK, "ok", "text/plain; charset=UTF-8");
            return;
        }
        if (result instanceof String text) {
            writeResponse(ctx, keepAlive, HttpResponseStatus.OK, text, "text/plain; charset=UTF-8");
            return;
        }
        writeResponse(ctx, keepAlive, HttpResponseStatus.OK,
                JSON.toJSONString(result), "application/json; charset=UTF-8");
    }

    private void writeResponse(ChannelHandlerContext ctx,
                               FullHttpRequest request,
                               HttpResponseStatus status,
                               String body,
                               String contentType) {
        writeResponse(ctx, HttpUtil.isKeepAlive(request), status, body, contentType);
    }

    private void writeResponse(ChannelHandlerContext ctx,
                               boolean keepAlive,
                               HttpResponseStatus status,
                               String body,
                               String contentType) {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                Unpooled.wrappedBuffer(bytes)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
            ctx.writeAndFlush(response);
        } else {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LogCore.core.error("service={} HTTP Netty 异常", service.getId(), cause);
        ctx.close();
    }
}
