package org.evd.game.AdminService.http;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;

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
    private final String method;
    private final String uri;
    private final String path;
    private final String clientAddr;
    private final String bodyText;
    private final JSONObject jsonBody;
    private final Map<String, Object> params;

    private HttpRequest(String method,
                        String uri,
                        String path,
                        String clientAddr,
                        String bodyText,
                        JSONObject jsonBody,
                        Map<String, Object> params) {
        this.method = method;
        this.uri = uri;
        this.path = path;
        this.clientAddr = clientAddr;
        this.bodyText = bodyText;
        this.jsonBody = jsonBody;
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    public static HttpRequest from(ChannelHandlerContext ctx, FullHttpRequest request) {
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        String bodyText = readBodyText(request);
        JSONObject jsonBody = parseJsonBody(bodyText);
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : decoder.parameters().entrySet()) {
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                continue;
            }
            params.put(entry.getKey(), values.getFirst());
        }
        if (jsonBody != null) {
            for (Map.Entry<String, Object> entry : jsonBody.entrySet()) {
                params.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return new HttpRequest(
                request.method().name(),
                request.uri(),
                HttpRequestMappingParser.normalizePath(decoder.path()),
                resolveClientAddr(ctx),
                bodyText,
                jsonBody,
                params
        );
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

    private static String resolveClientAddr(ChannelHandlerContext ctx) {
        SocketAddress remoteAddress = ctx.channel().remoteAddress();
        if (remoteAddress instanceof InetSocketAddress address) {
            String host = address.getAddress() == null ? address.getHostString() : address.getAddress().getHostAddress();
            return host + ":" + address.getPort();
        }
        return String.valueOf(remoteAddress);
    }
}
