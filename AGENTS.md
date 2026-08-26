# 开发约定

- 默认不要创建或修改测试类；仅在用户明确要求编写或更新测试时才执行。

## Actor 入口与业务类规范

- 所有由运行时注册的入口类和业务逻辑类都必须标注 `@Actor`。
- `@Rpc` 只能声明在标注 `@RpcHandler` 的类中；单独承担 RPC 的类使用 `XxxRpc` 后缀。
- `@ClientCmd` 只能声明在标注 `@ClientCmdHandler` 的类中；单独承担客户端命令的类使用 `XxxClientCmd` 后缀。
- 事件监听类必须标注 `@EventHandler` 并实现事件的 `Listener` 接口；单独承担事件的类使用 `XxxListener` 后缀。
- 没有入口标识、只承载业务实现的类使用 `XxxLogic` 后缀，并通过 `Service#getActor(XxxLogic.class)` 获取和调用。
- 一个类标注多个入口 Handler 时使用 `XxxHandler` 后缀；这种多标识类只允许在确实需要手写组合入口时使用，通常应拆成单标识类。
- `Service` 负责生命周期、服务级状态和基础设施；业务入口和业务逻辑应放到对应的 `@Actor` 类中。
- APT 必须校验上述类标识、`@Actor` 约束、入口注解归属和单标识命名规则；不要依赖运行时才发现归属错误。
