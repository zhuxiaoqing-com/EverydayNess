package ${commonPackageName};

import org.evd.game.runtime.call.CallPoint;
import org.evd.game.runtime.RPCProxyBase;
import org.evd.game.runtime.Service;
<#if singleton>
import org.evd.game.runtime.DistributeConfig;
</#if>
<#if fullClassName != ownerFullClassName>
import org.evd.game.runtime.mailbox.MailboxKey;
</#if>
<#if importPackages??>
    <#list importPackages as package>
        import ${package};
    </#list>
</#if>

/**
* 根据${className}Service生成的代理类
*/
public class ${className}Proxy extends RPCProxyBase {

    public final static class EnumCall{
    <#list methods as method>
        public final static int ${method.enumCall} = ${method.methodKey};
    </#list>
    }

    <#if singleton>
    private static final String SERV_NAME = "${serviceName}";
    private static CallPoint callPoint;

    public static ${className}Proxy inst() {
        ${className}Proxy proxy = new ${className}Proxy();
        if(callPoint == null){
            callPoint = DistributeConfig.getNode(SERV_NAME);
        }
        proxy.remote = callPoint;
        return proxy;
    }
    <#else>
    <#if fullClassName == ownerFullClassName>
    private ${className}Proxy(CallPoint callPoint){
        this.remote = callPoint;
    }
    public static ${className}Proxy inst(CallPoint callPoint) {
        return new ${className}Proxy(callPoint);
    }
    <#else>
    private MailboxKey mailboxKey;

    private ${className}Proxy(CallPoint callPoint, MailboxKey mailboxKey){
        this.remote = callPoint;
        this.mailboxKey = mailboxKey == null ? null : new MailboxKey(mailboxKey);
    }
    public static ${className}Proxy inst(CallPoint callPoint, MailboxKey mailboxKey) {
        return new ${className}Proxy(callPoint, mailboxKey);
    }
    </#if>
    </#if>

    <#list methods as method>
    /**
    * @see ${fullClassName}#${method.methodName}()
    */
    public ${method.returnType} ${method.methodName}(${method.formalParams}){
        Service service = Service.getCurrent();
        <#if method.returnType == "void">
        <#if method.targetIsOwner>
        service.call(remote, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}});
        <#else>
        service.call(remote, mailboxKey, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}});
        </#if>
        <#else >
        <#if method.targetIsOwner>
        return (${method.returnType})service.callWait(remote, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}});
        <#else>
        return (${method.returnType})service.callWait(remote, mailboxKey, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}});
        </#if>
        </#if>
    }
    <#if method.returnType != "void">
    public ${method.returnType} ${method.methodName}(${method.formalParams}<#if method.formalParams?has_content>, </#if>long timeoutMillis){
        Service service = Service.getCurrent();
        <#if method.targetIsOwner>
        return (${method.returnType})service.callWait(remote, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}}, timeoutMillis);
        <#else>
        return (${method.returnType})service.callWait(remote, mailboxKey, EnumCall.${method.enumCall}, new Object[]{${method.nameParams}}, timeoutMillis);
        </#if>
    }
    </#if>
    </#list>
}
