package org.evd.game.runtime.ymlconfig;

import org.evd.game.annotation.service.ServiceType;
import org.evd.game.runtime.support.exception.SysException;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NodeYml {
    private String dbConfigPath;
    /** 全局数据库部署形态，所有 Node 必须使用同一拓扑。 */
    private DbTopology dbTopology = DbTopology.REMOTE_SERVICE;
    private boolean debug;
    private int platformId;
    private int serverId;
    private LoginYml login = new LoginYml();
    private List<NodeInfo> nodes = new ArrayList<>();

    public String getDbConfigPath() {
        return dbConfigPath;
    }

    public void setDbConfigPath(String dbConfigPath) {
        this.dbConfigPath = dbConfigPath;
    }

    public DbTopology getDbTopology() {
        return dbTopology;
    }

    public void setDbTopology(DbTopology dbTopology) {
        this.dbTopology = dbTopology;
    }

    public List<NodeInfo> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeInfo> nodes) {
        this.nodes = nodes;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public int getPlatformId() {
        return platformId;
    }

    public void setPlatformId(int platformId) {
        this.platformId = platformId;
    }

    public int getServerId() {
        return serverId;
    }

    public void setServerId(int serverId) {
        this.serverId = serverId;
    }

    public LoginYml getLogin() {
        return login;
    }

    public void setLogin(LoginYml login) {
        this.login = login;
    }

    /**
     * 校验 Bootstrap 中完整的 Node 和 Service 配置。
     */
    public void validate(DbYml dbConfig) {
        if (login == null) {
            throw new SysException("login config is required");
        }
        login.validate();
        validateNodes();
        validateNodeServiceTypes();
        validateServiceNames();
        validateSingleServices();
        validateDbTopology(dbConfig);
    }

    private void validateNodes() {
        if (nodes == null || nodes.isEmpty()) {
            throw new SysException("bootstrap nodes is empty");
        }
        Set<Integer> nodeIds = new HashSet<>();
        for (NodeInfo nodeInfo : nodes) {
            if (nodeInfo == null) {
                throw new SysException("bootstrap contains null node");
            }
            if (nodeInfo.getNodeId() < 0) {
                throw new SysException("nodeId must be non-negative: name={}, nodeId={}",
                        nodeInfo.getName(), nodeInfo.getNodeId());
            }
            if (!nodeIds.add(nodeInfo.getNodeId())) {
                throw new SysException("duplicate nodeId: {}", nodeInfo.getNodeId());
            }
            if (nodeInfo.getName() == null || nodeInfo.getName().isBlank()) {
                throw new SysException("node name is required: nodeId={}", nodeInfo.getNodeId());
            }
        }
    }

    private void validateNodeServiceTypes() {
        for (NodeInfo nodeInfo : nodes) {
            if (nodeInfo.getNodeType() == null) {
                throw new SysException("nodeType is required: nodeId={}, name={}",
                        nodeInfo.getNodeId(), nodeInfo.getName());
            }
            if (nodeInfo.getSchedule() == null) {
                continue;
            }
            for (ScheduleInfo scheduleInfo : nodeInfo.getSchedule()) {
                if (scheduleInfo.getServices() == null) {
                    continue;
                }
                for (ServiceInfo serviceInfo : scheduleInfo.getServices()) {
                    if (serviceInfo == null || serviceInfo.getServiceType() == null) {
                        throw new SysException("serviceType is required: nodeId={}, name={}, schedule={}",
                                nodeInfo.getNodeId(), nodeInfo.getName(), scheduleInfo.getName());
                    }
                    ServiceType serviceType = serviceInfo.getServiceType();
                    if (!serviceType.isSupportNodeType(nodeInfo.getNodeType())) {
                        throw new SysException(
                                "serviceType does not support nodeType: nodeId={}, name={}, nodeType={}, serviceType={}, serviceNodeType={}",
                                nodeInfo.getNodeId(), nodeInfo.getName(), nodeInfo.getNodeType(), serviceType,
                                serviceType.getNodeType());
                    }
                }
            }
        }
    }

    private void validateSingleServices() {
        Map<ServiceType, Integer> serviceCountMap = new EnumMap<>(ServiceType.class);
        Map<ServiceType, List<String>> serviceSourceMap = new EnumMap<>(ServiceType.class);
        for (NodeInfo nodeInfo : nodes) {
            if (nodeInfo.getSchedule() == null) {
                continue;
            }
            for (ScheduleInfo scheduleInfo : nodeInfo.getSchedule()) {
                if (scheduleInfo.getServices() == null) {
                    continue;
                }
                for (ServiceInfo serviceInfo : scheduleInfo.getServices()) {
                    if (serviceInfo == null || serviceInfo.getServiceType() == null) {
                        continue;
                    }
                    ServiceType serviceType = serviceInfo.getServiceType();
                    if (!serviceType.isSingle()) {
                        continue;
                    }
                    int instanceCount = Math.max(1, serviceInfo.getNum());
                    serviceCountMap.merge(serviceType, instanceCount, Integer::sum);
                    serviceSourceMap.computeIfAbsent(serviceType, key -> new ArrayList<>())
                            .add(String.format("nodeId=%d,nodeName=%s,schedule=%s,service=%s,num=%d",
                                    nodeInfo.getNodeId(), nodeInfo.getName(), scheduleInfo.getName(),
                                    serviceInfo.getName(), instanceCount));
                }
            }
        }

        for (Map.Entry<ServiceType, Integer> entry : serviceCountMap.entrySet()) {
            if (entry.getValue() > 1) {
                throw new SysException("single service type duplicated: {} total={} detail={}",
                        entry.getKey(), entry.getValue(), serviceSourceMap.get(entry.getKey()));
            }
        }
    }

    private void validateServiceNames() {
        for (NodeInfo nodeInfo : nodes) {
            Set<String> serviceNames = new HashSet<>();
            if (nodeInfo.getSchedule() == null) {
                continue;
            }
            for (ScheduleInfo scheduleInfo : nodeInfo.getSchedule()) {
                if (scheduleInfo.getServices() == null) {
                    continue;
                }
                for (ServiceInfo serviceInfo : scheduleInfo.getServices()) {
                    if (serviceInfo == null) {
                        continue;
                    }
                    if (serviceInfo.getName() == null || serviceInfo.getName().isBlank()) {
                        throw new SysException(
                                "service name is required: nodeId={}, nodeName={}, schedule={}",
                                nodeInfo.getNodeId(), nodeInfo.getName(), scheduleInfo.getName());
                    }
                    int instanceCount = Math.max(1, serviceInfo.getNum());
                    // 按最终注册 ID 校验，不能只判断配置 name：例如 player(num=11) 与
                    // player1(num=1) 都会生成 player11，配置 name 不同但实例 ID 冲突。
                    for (int index = 1; index <= instanceCount; index++) {
                        String instanceName = serviceInfo.getInstanceName(index);
                        if (!serviceNames.add(instanceName)) {
                            throw new SysException(
                                    "duplicate service name in node: nodeId={}, nodeName={}, serviceName={}",
                                    nodeInfo.getNodeId(), nodeInfo.getName(), instanceName);
                        }
                    }
                }
            }
        }
    }

    private void validateDbTopology(DbYml dbConfig) {
        if (dbTopology == null) {
            throw new SysException("dbTopology is required");
        }
        if (dbTopology != DbTopology.NODE_LOCAL) {
            return;
        }

        for (NodeInfo nodeInfo : nodes) {
            if (nodeInfo.getSchedule() != null) {
                for (ScheduleInfo scheduleInfo : nodeInfo.getSchedule()) {
                    if (scheduleInfo.getServices() == null) {
                        continue;
                    }
                    for (ServiceInfo serviceInfo : scheduleInfo.getServices()) {
                        if (serviceInfo != null && serviceInfo.getServiceType() == ServiceType.DB) {
                            throw new SysException(
                                    "NODE_LOCAL mode must not configure DBService: nodeId={}, name={}",
                                    nodeInfo.getNodeId(), nodeInfo.getName());
                        }
                    }
                }
            }

        }

        if (dbConfig.getDb().getStorage().isEnableMemoryCache()) {
            throw new SysException("NODE_LOCAL mode must disable enableMemoryCache");
        }
    }
}
