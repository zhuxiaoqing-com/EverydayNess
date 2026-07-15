package org.evd.game.AdminService.http;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;
import org.evd.game.runtime.Service;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AdminService 通用 HTTP 请求对象。
 */
public final class HttpRequest {
    private final Service service;
    private final String method;
    private final String uri;
    private final String path;
    private final String clientAddr;
    private final String bodyText;
    private final JSONObject jsonBody;
    private final Map<String, Object> params;

    private HttpRequest(Service service,
                        String method,
                        String uri,
                        String path,
                        String clientAddr,
                        String bodyText,
                        JSONObject jsonBody,
                        Map<String, Object> params) {
        this.service = service;
        this.method = method;
        this.uri = uri;
        this.path = path;
        this.clientAddr = clientAddr;
        this.bodyText = bodyText;
        this.jsonBody = jsonBody;
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    private HttpRequest(String method,
                        String uri,
                        String path,
                        String clientAddr,
                        String bodyText,
                        JSONObject jsonBody,
                        Map<String, Object> params) {
        this(null, method, uri, path, clientAddr, bodyText, jsonBody, params);
    }

    public static HttpRequest from(ChannelHandlerContext ctx, FullHttpRequest request) {
        return from(null, ctx, request, HttpParameterValues.parse(request), null);
    }

    public static HttpRequest from(Service service,
                                   ChannelHandlerContext ctx,
                                   FullHttpRequest request) {
        return from(service, ctx, request, HttpParameterValues.parse(request), null);
    }

    static HttpRequest from(ChannelHandlerContext ctx,
                            FullHttpRequest request,
                            HttpParameterValues parameterValues) {
        return from(null, ctx, request, parameterValues, null);
    }

    static HttpRequest from(Service service,
                            ChannelHandlerContext ctx,
                            FullHttpRequest request,
                            HttpParameterValues parameterValues,
                            RequestType requestType) {
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        String bodyText = readBodyText(request);
        JSONObject jsonBody = requestType == RequestType.POST_JSON
                ? parseJsonBody(bodyText)
                : requestType == null ? parseJsonBody(request, bodyText) : null;
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : parameterValues.asMultiValueMap().entrySet()) {
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                continue;
            }
            params.put(entry.getKey(), values.size() == 1
                    ? values.getFirst()
                    : List.copyOf(values));
        }
        if (jsonBody != null) {
            for (Map.Entry<String, Object> entry : jsonBody.entrySet()) {
                params.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return new HttpRequest(
                service,
                request.method().name(),
                request.uri(),
                HttpRequestMappingParser.normalizePath(decoder.path()),
                resolveClientAddr(ctx),
                bodyText,
                jsonBody,
                params
        );
    }

    static HttpRequest from(ChannelHandlerContext ctx,
                            FullHttpRequest request,
                            HttpParameterValues parameterValues,
                            RequestType requestType) {
        return from(null, ctx, request, parameterValues, requestType);
    }

    public Service getService() {
        return service;
    }

    public <T extends Service> T getService(Class<T> serviceType) {
        if (serviceType == null) {
            throw new IllegalArgumentException("serviceType 不能为空");
        }
        return serviceType.cast(service);
    }

    public String getMethod() {
        return method;
    }

    public String getUri() {
        return uri;
    }

    public String getPath() {
        return path;
    }

    public String getClientAddr() {
        return clientAddr;
    }

    public String getBodyText() {
        return bodyText;
    }

    public JSONObject getJsonBody() {
        return jsonBody;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public <T> T toBean(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("type 不能为空");
        }
        Object source = (jsonBody == null || jsonBody.isEmpty()) ? params : jsonBody;
        return JSON.parseObject(JSON.toJSONString(source), type);
    }

    public Object get(String name) {
        return params.get(name);
    }

    public String getString(String name) {
        Object value = get(name);
        return value == null ? null : String.valueOf(value);
    }

    public Integer getInt(String name) {
        Object value = get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Integer.parseInt(text);
    }

    public Long getLong(String name) {
        Object value = get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Long.parseLong(text);
    }

    public Boolean getBoolean(String name) {
        Object value = get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        return Boolean.parseBoolean(text);
    }

    public Double getDouble(String name) {
        Object value = get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Double.parseDouble(text);
    }

    private static String readBodyText(FullHttpRequest request) {
        if (request.content() == null || !request.content().isReadable()) {
            return "";
        }
        return request.content().toString(CharsetUtil.UTF_8);
    }

    private static JSONObject parseJsonBody(String bodyText) {
        if (bodyText == null || bodyText.isBlank()) {
            return null;
        }
        Object parsed = JSON.parse(bodyText);
        return parsed instanceof JSONObject jsonObject ? jsonObject : null;
    }

    private static JSONObject parseJsonBody(FullHttpRequest request, String bodyText) {
        if (!HttpParameterValues.isJsonRequest(request)) {
            return null;
        }
        return parseJsonBody(bodyText);
    }

    private static String resolveClientAddr(ChannelHandlerContext ctx) {
        SocketAddress remoteAddress = ctx.channel().remoteAddress();
        if (remoteAddress instanceof InetSocketAddress address) {
            String host = address.getAddress() == null ? address.getHostString() : address.getAddress().getHostAddress();
            return host + ":" + address.getPort();
        }
        return String.valueOf(remoteAddress);
    }
}
