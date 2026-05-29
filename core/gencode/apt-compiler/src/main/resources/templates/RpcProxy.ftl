package ${commonPackageName};

import org.evd.game.runtime.Service;
<#if needsCallPointImport>
import org.evd.game.runtime.call.CallPoint;
</#if>
<#if needsLocationImport>
import org.evd.game.common.location.MessageLocationSender;
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
public final class ${className}Proxy {

    private ${className}Proxy() {
    }

    public final static class EnumCall{
    <#list methods as method>
        public final static int ${method.enumCall} = ${method.methodKey};
    </#list>
    }

    <#list methods as method>
    /**
    * @see ${fullClassName}#${method.methodName}()
    */
    public static ${method.returnType} ${method.methodName}(${method.targetPrefix}<#if method.formalParams?has_content>, </#if>${method.formalParams}){
        Service service = Service.getCurrent();
        <#if method.returnType == "void">
        <#if method.routeService>
        service.call(remote, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}});
        <#else>
        <#if method.usesFixedActorType>
        ActorId actorId = new ActorId(ActorType.${method.actorTypeName}, actorUniqueId);
        </#if>
        new MessageLocationSender().send(actorId, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}});
        </#if>
        <#else>
        <#if method.routeService>
        <#if method.usesFixedActorType>
        ActorId actorId = new ActorId(ActorType.${method.actorTypeName}, actorUniqueId);
        </#if>
        return (${method.returnType})service.callWait(remote, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}});
        <#else>
        <#if method.usesFixedActorType>
        ActorId actorId = new ActorId(ActorType.${method.actorTypeName}, actorUniqueId);
        </#if>
        return (${method.returnType})new MessageLocationSender().callWait(actorId, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}});
        </#if>
        </#if>
    }
    <#if method.returnType != "void">
    public static ${method.returnType} ${method.methodName}(${method.targetPrefix}, <#if method.formalParams?has_content>${method.formalParams}, </#if>long timeoutMillis){
        Service service = Service.getCurrent();
        <#if method.routeService>
        return (${method.returnType})service.callWait(remote, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}}, timeoutMillis);
        <#else>
        <#if method.usesFixedActorType>
        ActorId actorId = new ActorId(ActorType.${method.actorTypeName}, actorUniqueId);
        </#if>
        return (${method.returnType})new MessageLocationSender().callWait(actorId, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}}, timeoutMillis);
        </#if>
    }
    </#if>
    </#list>
}
