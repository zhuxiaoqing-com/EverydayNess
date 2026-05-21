package org.evd.game.gencode.client;

import org.evd.game.gencode.GenConst;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClientCmdRouteGenerator {
    private static final String SERVICES_DIR = "Services";
    private static final String REGISTRY_SUFFIX = "ClientCmdRegistry.java";
    private static final String ROUTER_PACKAGE = "org.evd.game.ConnService";
    private static final String ROUTER_CLASS_NAME = "ConnServiceClientCmdRouter";
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+);", Pattern.MULTILINE);
    private static final Pattern CASE_PATTERN = Pattern.compile("\\bcase\\s+([^:]+)\\s*:");
    private static final Pattern MSG_ID_IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+org\\.evd\\.game\\.common\\.proto\\.MsgId\\s*;", Pattern.MULTILINE);

    private ClientCmdRouteGenerator() {
    }

    public static void generate() {
        try {
            List<ClientCmdRouteInfo> routes = scanRoutes();
            if (routes.isEmpty()) {
                return;
            }
            writeRouter(routes);
        } catch (IOException e) {
            throw new RuntimeException("生成客户端协议总路由失败", e);
        }
    }

    private static List<ClientCmdRouteInfo> scanRoutes() throws IOException {
        Map<Integer, ClientCmdRouteInfo> routes = new LinkedHashMap<>();
        File servicesDir = new File(GenConst.ROOT_PROJECT_PATH, SERVICES_DIR);
        File[] serviceDirs = servicesDir.listFiles(File::isDirectory);
        if (serviceDirs == null) {
            return List.of();
        }
        for (File serviceDir : serviceDirs) {
            scanServiceRegistries(serviceDir, routes);
        }
        List<ClientCmdRouteInfo> result = new ArrayList<>(routes.values());
        result.sort(Comparator.comparingInt(ClientCmdRouteInfo::cmd));
        return result;
    }

    private static void scanServiceRegistries(File serviceDir, Map<Integer, ClientCmdRouteInfo> routes) throws IOException {
        Path genDir = serviceDir.toPath().resolve("src").resolve("gen").resolve("java");
        if (!Files.exists(genDir)) {
            return;
        }
        try (var paths = Files.walk(genDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(REGISTRY_SUFFIX))
                    .forEach(path -> loadRegistry(path, routes));
        }
    }

    private static void loadRegistry(Path registryPath, Map<Integer, ClientCmdRouteInfo> routes) {
        try {
            String content = Files.readString(registryPath, StandardCharsets.UTF_8);
            String packageName = parsePackageName(registryPath, content);
            String fileName = registryPath.getFileName().toString();
            String serviceClassName = fileName.substring(0, fileName.length() - REGISTRY_SUFFIX.length());
            Matcher caseMatcher = CASE_PATTERN.matcher(content);
            while (caseMatcher.find()) {
                String cmdExpr = caseMatcher.group(1).trim();
                int cmd = parseCmdValue(registryPath, content, cmdExpr);
                ClientCmdRouteInfo routeInfo = new ClientCmdRouteInfo(cmd, cmdExpr, packageName, serviceClassName);
                ClientCmdRouteInfo previous = routes.putIfAbsent(cmd, routeInfo);
                if (previous != null) {
                    throw new IllegalStateException("客户端协议重复注册: cmd=" + cmd
                            + ", service=" + previous.serviceFullClassName()
                            + ", service=" + routeInfo.serviceFullClassName());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("读取客户端协议分发表失败: " + registryPath, e);
        }
    }

    private static String parsePackageName(Path registryPath, String content) {
        Matcher matcher = PACKAGE_PATTERN.matcher(content);
        if (!matcher.find()) {
            throw new IllegalStateException("无法解析客户端协议分发表 package: " + registryPath);
        }
        return matcher.group(1);
    }

    private static void writeRouter(List<ClientCmdRouteInfo> routes) throws IOException {
        File targetDir = new File(GenConst.ROOT_PROJECT_PATH,
                "Services/ConnService/src/gen/java/" + ROUTER_PACKAGE.replace('.', '/'));
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        Path filePath = targetDir.toPath().resolve(ROUTER_CLASS_NAME + ".java");
        String content = buildRouterSource(routes);
        if (Files.exists(filePath)) {
            String existing = Files.readString(filePath, StandardCharsets.UTF_8);
            if (existing.equals(content)) {
                return;
            }
        }
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(filePath), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    private static int parseCmdValue(Path registryPath, String content, String cmdExpr) {
        if (cmdExpr.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(cmdExpr);
        }
        if (cmdExpr.startsWith("MsgId.") && cmdExpr.endsWith("_VALUE")) {
            if (!MSG_ID_IMPORT_PATTERN.matcher(content).find()) {
                throw new IllegalStateException("客户端协议分发表使用了 MsgId 但未导入: " + registryPath);
            }
            String constantName = cmdExpr.substring("MsgId.".length());
            try {
                return loadMsgIdValue(constantName);
            } catch (IOException e) {
                throw new IllegalStateException("解析 MsgId 常量失败: " + cmdExpr + ", file=" + registryPath, e);
            }
        }
        throw new IllegalStateException("无法解析客户端协议号表达式: " + cmdExpr + ", file=" + registryPath);
    }

    private static int loadMsgIdValue(String constantName) throws IOException {
        Path msgIdJava = Path.of(GenConst.ROOT_PROJECT_PATH, "common", "build", "generated", "source",
                "proto", "main", "java", "org", "evd", "game", "common", "proto", "MsgId.java");
        String content = Files.readString(msgIdJava, StandardCharsets.UTF_8);
        Pattern pattern = Pattern.compile("public\\s+static\\s+final\\s+int\\s+"
                + Pattern.quote(constantName) + "\\s*=\\s*(\\d+)\\s*;");
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            throw new IllegalStateException("MsgId.java 中找不到常量: " + constantName);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static String buildRouterSource(List<ClientCmdRouteInfo> routes) {
        StringBuilder source = new StringBuilder();
        source.append("package ").append(ROUTER_PACKAGE).append(";\n\n");
        source.append("import org.evd.game.common.proto.MsgId;\n");
        source.append("import org.evd.game.runtime.Chunk;\n");
        source.append("import org.evd.game.runtime.ClientSessionRef;\n");
        source.append("import org.evd.game.runtime.DistributeConfig;\n");
        source.append("import org.evd.game.runtime.netty.NetChannel;\n");
        source.append("import org.evd.game.runtime.call.CallPoint;\n");
        for (String proxyImport : collectProxyImports(routes)) {
            source.append("import ").append(proxyImport).append(";\n");
        }
        source.append("\n");
        source.append("/**\n");
        source.append(" * 根据所有客户端协议分发表聚合生成的总路由\n");
        source.append(" */\n");
        source.append("public final class ").append(ROUTER_CLASS_NAME).append(" {\n");
        source.append("    private ").append(ROUTER_CLASS_NAME).append("() {\n");
        source.append("    }\n\n");
        source.append("    public static void forward(ConnService owner, NetChannel session, int cmd, byte[] body) {\n");
        source.append("        ClientSessionRef sessionRef = owner.buildClientSessionRef(session);\n");
        source.append("        switch (cmd) {\n");
        for (ClientCmdRouteInfo route : routes) {
            source.append("            case ").append(route.cmdExpr()).append(":\n");
            source.append("                forwardTo").append(route.serviceClassName()).append("(sessionRef, cmd, body);\n");
            source.append("                return;\n");
        }
        source.append("            default:\n");
        source.append("                throw new IllegalStateException(\"未注册的客户端协议: cmd=\" + cmd);\n");
        source.append("        }\n");
        source.append("    }\n\n");
        for (ClientCmdRouteInfo route : routes) {
            source.append("    private static void forwardTo").append(route.serviceClassName())
                    .append("(ClientSessionRef session, int cmd, byte[] body) {\n");
            source.append("        CallPoint callPoint = DistributeConfig.getNodeByServiceClass(\"")
                    .append(route.serviceFullClassName()).append("\", session.getRouteKey());\n");
            source.append("        if (callPoint == null) {\n");
            source.append("            throw new IllegalStateException(\"找不到客户端协议目标服务: cmd=")
                    .append(route.cmd()).append(", service=").append(route.serviceFullClassName()).append("\");\n");
            source.append("        }\n");
            source.append("        ").append(route.proxyClassName())
                    .append(".forwardClientCmd(callPoint, session, cmd, new Chunk(body));\n");
            source.append("    }\n\n");
        }
        source.append("}\n");
        return source.toString();
    }

    private static Set<String> collectProxyImports(List<ClientCmdRouteInfo> routes) {
        Set<String> imports = new LinkedHashSet<>();
        for (ClientCmdRouteInfo route : routes) {
            imports.add("org.evd.game.common.proxy." + route.proxyClassName());
        }
        return imports;
    }
}
