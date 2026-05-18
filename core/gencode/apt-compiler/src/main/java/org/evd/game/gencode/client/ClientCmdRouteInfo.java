package org.evd.game.gencode.client;

public record ClientCmdRouteInfo(
        int cmd,
        String cmdExpr,
        String servicePackageName,
        String serviceClassName
) {
    public String serviceFullClassName() {
        return servicePackageName + "." + serviceClassName;
    }

    public String proxyClassName() {
        return serviceClassName + "Proxy";
    }
}
