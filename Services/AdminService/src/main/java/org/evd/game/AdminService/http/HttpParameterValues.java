package org.evd.game.AdminService.http;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.util.CharsetUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP Query 和表单参数的统一多值表示。
 *
 * <p>保留多值是必要的，因为同名参数可以映射到 List，例如：
 * {@code itemIds=1&itemIds=2}。</p>
 */
public final class HttpParameterValues {
    private final Map<String, List<String>> values = new LinkedHashMap<>();

    public static HttpParameterValues parse(FullHttpRequest request) {
        return parse(request, null);
    }

    static HttpParameterValues parse(FullHttpRequest request, RequestType requestType) {
        HttpParameterValues result = new HttpParameterValues();
        if (requestType == null || requestType == RequestType.GET) {
            QueryStringDecoder queryDecoder = new QueryStringDecoder(request.uri(), CharsetUtil.UTF_8);
            result.addAll(queryDecoder.parameters());
        }

        if (requestType == RequestType.POST_FORM
                || (requestType == null && isFormRequest(request))) {
            result.addFormBody(request);
        }
        return result;
    }

    public void add(String name, String value) {
        if (name == null) {
            throw new IllegalArgumentException("HTTP 参数名不能为空");
        }
        values.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
    }

    public void addAll(Map<String, List<String>> source) {
        source.forEach((name, list) -> {
            if (list != null) {
                list.forEach(value -> add(name, value));
            }
        });
    }

    public String first(String name) {
        List<String> list = values.get(name);
        return list == null || list.isEmpty() ? null : list.getFirst();
    }

    public List<String> all(String name) {
        List<String> list = values.get(name);
        return list == null ? List.of() : List.copyOf(list);
    }

    public Map<String, List<String>> asMultiValueMap() {
        LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>();
        values.forEach((name, list) -> copy.put(name, List.copyOf(list)));
        return Collections.unmodifiableMap(copy);
    }

    public Map<String, Object> asObjectMap() {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        values.forEach((name, list) -> {
            if (list.isEmpty()) {
                result.put(name, null);
            } else if (list.size() == 1) {
                result.put(name, list.getFirst());
            } else {
                result.put(name, List.copyOf(list));
            }
        });
        return result;
    }

    static boolean isFormRequest(FullHttpRequest request) {
        String mediaType = mediaType(request);
        return "application/x-www-form-urlencoded".equals(mediaType)
                || "multipart/form-data".equals(mediaType);
    }

    static boolean isJsonRequest(FullHttpRequest request) {
        return "application/json".equals(mediaType(request)) || mediaType(request).endsWith("+json");
    }

    private static String mediaType(FullHttpRequest request) {
        String contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE, "");
        int separator = contentType.indexOf(';');
        String mediaType = separator < 0 ? contentType : contentType.substring(0, separator);
        return mediaType.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void addFormBody(FullHttpRequest request) {
        if (!request.method().name().equals("POST") || !request.content().isReadable()) {
            return;
        }

        HttpPostRequestDecoder decoder = null;
        try {
            decoder = new HttpPostRequestDecoder(
                    new DefaultHttpDataFactory(false), request, CharsetUtil.UTF_8);
            for (InterfaceHttpData data : decoder.getBodyHttpDatas()) {
                if (data.getHttpDataType() != InterfaceHttpData.HttpDataType.Attribute) {
                    continue;
                }
                Attribute attribute = (Attribute) data;
                add(attribute.getName(), attribute.getValue());
            }
        } catch (IOException | HttpPostRequestDecoder.ErrorDataDecoderException e) {
            throw new IllegalArgumentException("解析 HTTP 表单参数失败", e);
        } finally {
            if (decoder != null) {
                decoder.destroy();
            }
        }
    }
}
