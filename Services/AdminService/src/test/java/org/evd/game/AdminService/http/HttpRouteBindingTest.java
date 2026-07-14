package org.evd.game.AdminService.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import org.evd.game.AdminService.controller.HttpBindingExampleController;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpRouteBindingTest {
    private final HttpRequestMappingParser parser = new HttpRequestMappingParser();

    @Test
    void routeMetadata_shouldKeepRequestTypeParameterNameAndGenericType() {
        HttpRouteDefinition definition = route("/test/get/scalars");

        assertEquals(RequestType.GET, definition.requestType());
        assertEquals("playerId", definition.parameters().get(0).name());
        assertEquals(long.class, definition.parameters().get(0).parameterType());

        HttpRouteParameter listParameter = route("/test/get/list").parameters().get(0);
        assertEquals(List.class, listParameter.parameterType());
        assertInstanceOf(ParameterizedType.class, listParameter.genericType());
    }

    @Test
    void classRoute_shouldBePrefixedToMethodRoute() {
        Map<String, HttpRouteDefinition> routes = parser
                .parseControllers(new ClassRouteController());

        assertEquals(RequestType.GET, routes.get("/test/class-base/scalars").requestType());
        assertEquals(RequestType.POST_FORM, routes.get("/test/class-base/form").requestType());
    }

    @Test
    void controllerContextParameter_shouldBeAllowedWithScalarParameters() {
        HttpRouteDefinition definition = parser
                .parseControllers(new HttpBindingExampleController())
                .get("/example/http-binding/form-scalars");

        assertEquals(HttpRequest.class, definition.parameters().get(0).parameterType());
        assertEquals(long.class, definition.parameters().get(1).parameterType());
        assertEquals(String.class, definition.parameters().get(2).parameterType());
    }

    @Test
    void invalidParameterCombinations_shouldFailDuringRouteRegistration() {
        assertThrows(IllegalStateException.class,
                () -> parser.parseControllers(new InvalidMixedController()));
        assertThrows(IllegalStateException.class,
                () -> parser.parseControllers(new InvalidTwoComplexController()));
        assertThrows(IllegalStateException.class,
                () -> parser.parseControllers(new InvalidListController()));
        assertThrows(IllegalStateException.class,
                () -> parser.parseControllers(new InvalidJsonScalarsController()));
        assertThrows(IllegalStateException.class,
                () -> parser.parseControllers(new InvalidJsonListController()));
        assertThrows(IllegalStateException.class,
                () -> parser.parseControllers(new InvalidJsonMapController()));
        assertThrows(IllegalStateException.class,
                () -> parser.parseControllers(new InvalidGetObjectController()));
        assertThrows(IllegalStateException.class,
                () -> parser.parseControllers(new InvalidPostFormObjectController()));
    }

    @Test
    void get_shouldBindScalarParametersAndRepeatedListValues() throws Exception {
        Object[] scalarArgs = bind(
                "/test/get/scalars",
                request(HttpMethod.GET, "/test/get/scalars?playerId=10001&serverId=2", null, "")
        );
        assertEquals(10001L, scalarArgs[0]);
        assertEquals(2, scalarArgs[1]);

        Object[] listArgs = bind(
                "/test/get/list",
                request(HttpMethod.GET, "/test/get/list?itemIds=11&itemIds=12&itemIds=13", null, "")
        );
        assertEquals(List.of(11L, 12L, 13L), listArgs[0]);

    }

    @Test
    void postForm_shouldBindMaps() throws Exception {
        Object[] singleMapArgs = bind(
                "/test/post-form/map",
                request(
                        HttpMethod.POST,
                        "/test/post-form/map",
                        "application/x-www-form-urlencoded",
                        "name=first&name=second&level=20"
                )
        );
        assertEquals(Map.of("name", "first", "level", "20"), singleMapArgs[0]);

        Object[] multiMapArgs = bind(
                "/test/post-form/multi-map",
                request(
                        HttpMethod.POST,
                        "/test/post-form/multi-map",
                        "application/x-www-form-urlencoded",
                        "name=first&name=second&level=20"
                )
        );
        assertEquals(Map.of(
                "name", List.of("first", "second"),
                "level", List.of("20")
        ), multiMapArgs[0]);
    }

    @Test
    void postJson_shouldBindOneObject() throws Exception {
        Object[] objectArgs = bind(
                "/test/post-json/object",
                request(
                        HttpMethod.POST,
                        "/test/post-json/object",
                        "application/json; charset=UTF-8",
                        "{\"playerId\":10001,\"name\":\"test\",\"level\":20}"
                )
        );
        BindingRequest object = assertInstanceOf(BindingRequest.class, objectArgs[0]);
        assertEquals(10001L, object.playerId());
        assertEquals("test", object.name());
        assertEquals(20, object.level());
    }

    @Test
    void routeType_shouldRejectWrongMethodAndContentType() throws Exception {
        Method validate = AdminHttpServerInboundHandler.class.getDeclaredMethod(
                "validateRequestType", RequestType.class, FullHttpRequest.class);
        validate.setAccessible(true);

        FullHttpRequest wrongMethod = request(
                HttpMethod.GET,
                "/test/post-form/object",
                null,
                ""
        );
        try {
            assertEquals(
                    io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED,
                    validate.invoke(null, RequestType.POST_FORM, wrongMethod)
            );
        } finally {
            wrongMethod.release();
        }

        FullHttpRequest wrongContentType = request(
                HttpMethod.POST,
                "/test/post-form/object",
                "application/json",
                "{}"
        );
        try {
            assertEquals(
                    io.netty.handler.codec.http.HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE,
                    validate.invoke(null, RequestType.POST_FORM, wrongContentType)
            );
        } finally {
            wrongContentType.release();
        }
    }

    private HttpRouteDefinition route(String path) {
        return parser.parseControllers(new BindingController()).get(path);
    }

    private Object[] bind(String path, FullHttpRequest request) throws Exception {
        HttpRouteDefinition definition = route(path);
        AdminHttpServerInboundHandler handler = new AdminHttpServerInboundHandler(null);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        try {
            ChannelHandlerContext context = channel.pipeline().context(handler);
            Method buildArgs = AdminHttpServerInboundHandler.class.getDeclaredMethod(
                    "buildArgs",
                    ChannelHandlerContext.class,
                    List.class,
                    FullHttpRequest.class,
                    RequestType.class
            );
            buildArgs.setAccessible(true);
            try {
                return (Object[]) buildArgs.invoke(
                        handler,
                        context,
                        definition.parameters(),
                        request,
                        definition.requestType()
                );
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof Exception checkedException) {
                    throw checkedException;
                }
                throw exception;
            } finally {
                request.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static FullHttpRequest request(
            HttpMethod method,
            String uri,
            String contentType,
            String body
    ) {
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                method,
                uri,
                Unpooled.copiedBuffer(body, CharsetUtil.UTF_8)
        );
        if (contentType != null) {
            request.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        }
        return request;
    }

    public static class BindingController {
        @HttpRoute(value = "/test/get/scalars", type = RequestType.GET)
        public void getScalars(long playerId, int serverId) {
        }

        @HttpRoute(value = "/test/get/list", type = RequestType.GET)
        public void getList(List<Long> itemIds) {
        }

        @HttpRoute(value = "/test/post-form/map", type = RequestType.POST_FORM)
        public void postFormMap(Map<String, String> parameters) {
        }

        @HttpRoute(value = "/test/post-form/multi-map", type = RequestType.POST_FORM)
        public void postFormMultiMap(Map<String, List<String>> parameters) {
        }

        @HttpRoute(value = "/test/post-json/object", type = RequestType.POST_JSON)
        public void postJsonObject(BindingRequest request) {
        }
    }

    @HttpRoute("/test/class-base")
    public static class ClassRouteController {
        @HttpRoute(value = "/scalars", type = RequestType.GET)
        public void scalars(long playerId) {
        }

        @HttpRoute(value = "/form", type = RequestType.POST_FORM)
        public void form(Map<String, String> parameters) {
        }
    }

    public record BindingRequest(Long playerId, String name, Integer level) {
    }

    public static class InvalidMixedController {
        @HttpRoute(value = "/test/invalid/mixed", type = RequestType.GET)
        public void invalid(long playerId, BindingRequest request) {
        }
    }

    public static class InvalidTwoComplexController {
        @HttpRoute(value = "/test/invalid/two-complex", type = RequestType.GET)
        public void invalid(BindingRequest first, BindingRequest second) {
        }
    }

    public static class InvalidListController {
        @HttpRoute(value = "/test/invalid/list", type = RequestType.GET)
        public void invalid(List<BindingRequest> requests) {
        }
    }

    public static class InvalidGetObjectController {
        @HttpRoute(value = "/test/invalid/get-object", type = RequestType.GET)
        public void invalid(BindingRequest request) {
        }
    }

    public static class InvalidPostFormObjectController {
        @HttpRoute(value = "/test/invalid/post-form-object", type = RequestType.POST_FORM)
        public void invalid(BindingRequest request) {
        }
    }

    public static class InvalidJsonScalarsController {
        @HttpRoute(value = "/test/invalid/json-scalars", type = RequestType.POST_JSON)
        public void invalid(long playerId, int serverId) {
        }
    }

    public static class InvalidJsonListController {
        @HttpRoute(value = "/test/invalid/json-list", type = RequestType.POST_JSON)
        public void invalid(List<Long> itemIds) {
        }
    }

    public static class InvalidJsonMapController {
        @HttpRoute(value = "/test/invalid/json-map", type = RequestType.POST_JSON)
        public void invalid(Map<String, Object> parameters) {
        }
    }
}
