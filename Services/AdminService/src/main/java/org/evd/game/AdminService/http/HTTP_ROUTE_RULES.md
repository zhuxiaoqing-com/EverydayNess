# Admin HTTP 路由参数规则

## 1. 路由声明

所有 Admin HTTP Controller 方法必须使用 `@HttpRoute`，并明确声明
`RequestType`：

```java
@HttpRoute(value = "/admin/role/query", type = RequestType.GET)
public HttpResult<RoleVO> query(HttpRequest ctx, Long roleId) {
    // ...
}
```

也可以在 Controller 类上声明公共 URI 前缀，方法上的 URI 会与它拼接：

```java
@HttpRoute("/admin/role")
public class RoleController {
    @HttpRoute(value = "/query", type = RequestType.GET)
    public HttpResult<RoleVO> query(Long roleId) {
        // 最终 URI：/admin/role/query
    }
}
```

类级别的 `@HttpRoute` 只提供 URI 前缀，方法级别仍然必须声明请求类型。

支持的请求类型：

| 类型 | HTTP 方法 | 参数来源 |
| --- | --- | --- |
| `GET` | `GET` | URL Query 参数 |
| `POST_FORM` | `POST` | `application/x-www-form-urlencoded` 或 multipart 普通字段 |
| `POST_JSON` | `POST` | `application/json` 或 `application/*+json` 请求体 |

请求方法或 `Content-Type` 与路由声明不匹配时，请求会被拒绝。

## 2. 参数允许规则

### GET 和 POST_FORM

允许以下参数组合：

```java
public Result query(long playerId, int serverId)
public Result delete(List<Long> itemIds)
public Result execute(Map<String, String> parameters)
public Result execute(Map<String, List<String>> parameters)
public Result execute(Map<String, Object> parameters)
```

其中：

- 基础类型支持 primitive、包装类型和 `String`。
- `List` 必须声明泛型，并且元素只能是基础类型。
- `Map` 的 key 必须是 `String`。
- `Map<String, String>` 遇到重复字段时取第一个值。
- `Map<String, List<String>>` 保留同名字段的全部值。
- `Map<String, Object>` 单值转为字符串，多值转为 List。
- 不允许自定义 DTO、record 或其他复杂对象。

示例：

```text
GET /admin/item/delete?playerId=10001&itemIds=11&itemIds=12
```

```text
playerId=10001&itemIds=11&itemIds=12
```

### POST_JSON

JSON 请求只能使用一个自定义对象接收：

```java
@HttpRoute(value = "/admin/role/ban", type = RequestType.POST_JSON)
public HttpResult<Void> ban(HttpRequest ctx, BanRequest request) {
    // ...
}
```

```json
{
  "roleId": 10001,
  "minutes": 30,
  "reason": "spam"
}
```

以下形式不允许：

```java
public Result execute(long roleId, String reason)
public Result execute(List<Long> itemIds)
public Result execute(Map<String, Object> parameters)
public Result execute(BanRequest first, BanRequest second)
```

## 3. 框架参数

方法最多允许一个框架参数：

```java
HttpRequest
FullHttpRequest
```

框架参数不参与普通参数数量限制，因此下面写法合法：

```java
public Result query(HttpRequest ctx, long roleId, String name)
public Result ban(HttpRequest ctx, BanRequest request)
```

不支持直接注入 `ChannelHandlerContext`，需要请求上下文时使用
`HttpRequest`。

`HttpRequest` 会携带当前运行的 `Service`，可以在 Controller 中透传调用：

```java
public Result execute(HttpRequest ctx) {
    Service service = ctx.getService();
    // 使用当前 AdminService 的 Service 实例调用业务能力
}
```

如果需要调用 `AdminService` 自定义方法，可以使用类型化获取：

```java
AdminService service = ctx.getService(AdminService.class);
```

## 4. 方法合法性

路由注册时会检查方法签名：

- 方法必须是 `public`、非 `static`。
- 最多一个 `HttpRequest` 或 `FullHttpRequest`。
- GET/POST_FORM 不允许复杂对象。
- POST_JSON 必须且只能有一个复杂对象。
- 复杂对象不能与基础类型、List 或 Map 混用。
- `List` 必须声明泛型，例如 `List<Long>`。
- 不支持 `BigDecimal`、`BigInteger`、`UUID` 和枚举作为直接参数或 List 元素。

参数名来自 Java 编译时保留的参数名，项目已启用 `-parameters`。因此推荐
直接使用清晰的参数名，例如 `playerId`、`itemIds`。

## 5. 推荐写法

```java
@HttpRoute(value = "/admin/role/query", type = RequestType.GET)
public HttpResult<RoleVO> query(HttpRequest ctx, Long roleId) {
    // GET 参数使用基础类型
}

@HttpRoute(value = "/admin/item/delete", type = RequestType.POST_FORM)
public HttpResult<Void> delete(HttpRequest ctx, List<Long> itemIds) {
    // 表单参数使用 List 或 Map
}

@HttpRoute(value = "/admin/role/ban", type = RequestType.POST_JSON)
public HttpResult<Void> ban(HttpRequest ctx, BanRequest request) {
    // JSON 参数使用一个 DTO
}
```
