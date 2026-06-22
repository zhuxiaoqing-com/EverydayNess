package ${commonPackageName};

<#if needsServiceImport>
import org.evd.game.runtime.Service;
</#if>
<#if needsCallPointImport>
import org.evd.game.runtime.call.CallPoint;
</#if>
<#if needsLocationImport>
import org.evd.game.runtime.mailbox.MessageLocationSender;
</#if>
<#if needsActorIdImport>
import org.evd.game.runtime.actor.ActorId;
</#if>
<#if needsActorTypeImport>
import org.evd.game.runtime.actor.ActorType;
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
    <#if needsLocationImport>
    private static final MessageLocationSender locationSender = new MessageLocationSender();
    </#if>

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
        locationSender.send(actorId, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}});
        </#if>
        <#else>
        <#if method.routeService>
        Service service = Service.getCurrent();
        return (${method.returnType})service.callWait(remote, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}});
        <#else>
        ActorId actorId = new ActorId(ActorType.${method.actorTypeName}, actorUniqueId);
        return (${method.returnType})locationSender.callWait(actorId, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}});
        </#if>
        </#if>
    }

    <#if method.returnType != "void">
    public ${method.returnType} ${method.methodName}(${method.targetPrefix}, <#if method.formalParams?has_content>${method.formalParams}, </#if>long timeoutMillis){
        <#if method.routeService>
        Service service = Service.getCurrent();
        return (${method.returnType})service.callWait(remote, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}}, timeoutMillis);
        <#else>
        ActorId actorId = new ActorId(ActorType.${method.actorTypeName}, actorUniqueId);
        return (${method.returnType})locationSender.callWait(actorId, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}}, timeoutMillis);
        </#if>
    }
    </#if>

    </#list>
}
