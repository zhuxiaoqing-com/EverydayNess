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
import org.evd.game.runtime.support.LogCore;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 直接按路径分发到 AdminService controller。
 */
public final class AdminHttpServerInboundHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final AdminHttpRouteRegistry routeRegistry;

    public AdminHttpServerInboundHandler(AdminHttpRouteRegistry routeRegistry) {
        this.routeRegistry = routeRegistry;
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
                LogCore.core.warn("AdminService HTTP 路由不存在: method={}, path={}", request.method(), routeKey);
                writeResponse(ctx, request, HttpResponseStatus.NOT_FOUND, "route not found: " + routeKey, "text/plain; charset=UTF-8");
                return;
            }
            Object result = invokeRoute(ctx, routeDefinition, request);
            LogCore.core.info("AdminService HTTP 请求: method={}, path={}, handler={}#{}",
                    request.method(), routeKey, routeDefinition.controllerClassName(), routeDefinition.methodName());
            writeSuccessResponse(ctx, request, result);
        } catch (Throwable e) {
            LogCore.core.error("AdminService HTTP 调用失败: method={}, path={}", request.method(), routeKey, e);
            writeResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR, e.getMessage(), "text/plain; charset=UTF-8");
        }
    }

    private Object invokeRoute(ChannelHandlerContext ctx,
                               HttpRouteDefinition routeDefinition,
                               FullHttpRequest request) throws Throwable {
        Method method = routeDefinition.method();
        Object[] args = buildArgs(ctx, routeDefinition.parameters(), request);
        try {
            return method.invoke(routeDefinition.controller(), args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    private Object[] buildArgs(ChannelHandlerContext ctx,
                               List<HttpRouteParameter> parameters,
                               FullHttpRequest request) {
        Object[] args = new Object[parameters.size()];
        String bodyText = readBodyText(request);
        JSONObject bodyJson = null;
        HttpRequest httpRequest = null;
        for (int i = 0; i < parameters.size(); i++) {
            Class<?> parameterType = parameters.get(i).parameterType();
            if (HttpRequest.class.isAssignableFrom(parameterType)) {
                if (httpRequest == null) {
                    httpRequest = HttpRequest.from(ctx, request);
                }
                args[i] = httpRequest;
                continue;
            }
            if (FullHttpRequest.class.isAssignableFrom(parameterType)) {
                args[i] = request;
                continue;
            }
            if (String.class == parameterType) {
                args[i] = bodyText;
                continue;
            }
            if (JSONObject.class == parameterType) {
                if (bodyJson == null) {
                    bodyJson = bodyText.isBlank() ? new JSONObject() : JSON.parseObject(bodyText);
                }
                args[i] = bodyJson;
                continue;
            }
            if (httpRequest == null) {
                httpRequest = HttpRequest.from(ctx, request);
            }
            args[i] = httpRequest.toBean(parameterType);
        }
        return args;
    }

    private String readBodyText(FullHttpRequest request) {
        ByteBuf content = request.content();
        if (content == null || !content.isReadable()) {
            return "";
        }
        return content.toString(CharsetUtil.UTF_8);
    }

    private void writeSuccessResponse(ChannelHandlerContext ctx, FullHttpRequest request, Object result) {
        if (result == null) {
            writeResponse(ctx, request, HttpResponseStatus.OK, "ok", "text/plain; charset=UTF-8");
            return;
        }
        if (result instanceof String text) {
            writeResponse(ctx, request, HttpResponseStatus.OK, text, "text/plain; charset=UTF-8");
            return;
        }
        writeResponse(ctx, request, HttpResponseStatus.OK, JSON.toJSONString(result), "application/json; charset=UTF-8");
    }

    private void writeResponse(ChannelHandlerContext ctx,
                               FullHttpRequest request,
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
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
            ctx.writeAndFlush(response);
        } else {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LogCore.core.error("AdminService HTTP Netty 异常", cause);
        ctx.close();
    }
}
