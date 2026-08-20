package ${commonPackageName};

<#if needsServiceImport>
import org.evd.game.runtime.Service;
</#if>
<#if generateResultMethods>
import org.evd.game.runtime.rpcProxyInterface.RpcResult;
</#if>
<#if needsCallPointImport>
import org.evd.game.runtime.call.CallPoint;
</#if>
<#if needsActorIdImport>
import org.evd.game.runtime.actor.ActorId;
</#if>
<#if needsActorTypeImport>
import org.evd.game.annotation.ActorType;
</#if>
<#if importPackages??>
<#list importPackages as package>
import ${package};
</#list>
</#if>

/**
* 根据${className}Service生成的代理类
*/
public final class ${generatedClassName}<#if implementsProxyInterface> implements ${proxyInterfaceSimpleName}</#if> {

    private static final ${generatedClassName} INSTANCE = new ${generatedClassName}();

    private ${generatedClassName}() {
    }

    public static ${generatedClassName} inst() {
        return INSTANCE;
    }

    public final static class EnumCall{
    <#list methods as method>
        public final static int ${method.enumCall} = ${method.methodKey};
    </#list>
    }

    <#if generateResultMethods>
    <#list methods as method>
    <#if method.generateResultMethod>
    <#if method.isVoid>
    /**
    * 对应 void RPC 的发送结果版本；只表示本地发送是否成功，不等待远端执行结果。
    */
    public static RpcResult<Void> ${method.resultMethodName}(${method.resultFormalParams}){
        return RpcResult.run(() -> inst().${method.methodName}(${method.resultCallArgs}));
    }
    <#else>
    /**
    * 对应源方法的结果版本；远端错误、断链和超时均通过 RpcResult 返回。
    */
    public static RpcResult<${method.returnTypeWrapper}> ${method.resultMethodName}(${method.resultFormalParams}){
        return RpcResult.call(() -> inst().${method.methodName}(${method.resultCallArgs}));
    }

    <#if generateTimeoutOverloads>
    public static RpcResult<${method.returnTypeWrapper}> ${method.resultMethodName}(${method.resultFormalParams}, long timeoutMillis){
        return RpcResult.call(() -> inst().${method.methodName}(${method.resultCallArgs}, timeoutMillis));
    }
    </#if>
    </#if>

    </#if>
    </#list>
    </#if>

    <#list methods as method>
    /**
    * 对应源方法: ${fullClassName}#${method.methodName}()
    */
    public ${method.returnType} ${method.methodName}(${method.targetPrefix}<#if method.formalParams?has_content>, </#if>${method.formalParams}){
        <#if method.returnType == "void">
        <#if method.routeService>
        Service service = Service.getCurrent();
        service.call(remote, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}});
        <#else>
        ActorId actorId = new ActorId(ActorType.${method.actorTypeName}, actorUniqueId);
        Service.getCurrent().getMessageLocationSender().send(actorId, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}});
        </#if>
        <#else>
        <#if method.routeService>
        Service service = Service.getCurrent();
        return (${method.returnType})service.callWait(remote, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}});
        <#else>
        ActorId actorId = new ActorId(ActorType.${method.actorTypeName}, actorUniqueId);
        return (${method.returnType})Service.getCurrent().getMessageLocationSender().callWait(actorId, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}});
        </#if>
        </#if>
    }

    <#if generateTimeoutOverloads && method.hasResult>
    public ${method.returnType} ${method.methodName}(${method.targetPrefix}, <#if method.formalParams?has_content>${method.formalParams}, </#if>long timeoutMillis){
        <#if method.routeService>
        Service service = Service.getCurrent();
        return (${method.returnType})service.callWait(remote, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}}, timeoutMillis);
        <#else>
        ActorId actorId = new ActorId(ActorType.${method.actorTypeName}, actorUniqueId);
        return (${method.returnType})Service.getCurrent().getMessageLocationSender().callWait(actorId, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}}, timeoutMillis);
        </#if>
    }
    </#if>

    </#list>
}
