package org.evd.game.runtime.Db.table;

import org.evd.game.annotation.service.ServiceName;
import org.evd.game.annotation.service.ServiceType;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public interface TableRegistry {
    String REGISTRY_SUFFIX = ".db.DbTableRegistry";

    void register(DbTableRegistrar registrar);

    static boolean existsFor(Class<?> ownerClass) {
        String registryClassName = ownerClass.getPackageName() + REGISTRY_SUFFIX;
        try {
            Class.forName(registryClassName, false, ownerClass.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static TableRegistry loadFor(Class<?> ownerClass) {
        String registryClassName = ownerClass.getPackageName() + REGISTRY_SUFFIX;
        try {
            Class<?> registryClass = Class.forName(registryClassName, true, ownerClass.getClassLoader());
            if (!TableRegistry.class.isAssignableFrom(registryClass)) {
                throw new IllegalStateException("DbTableRegistry 未实现 TableRegistry: " + registryClassName);
            }
            Constructor<?> constructor = registryClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (TableRegistry) constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("加载 DbTableRegistry 失败: " + registryClassName, e);
        }
    }

    /** 从 ServiceType 枚举加载全部 Service 类，并按枚举顺序去重。 */
    static List<Class<?>> allServiceClasses() throws ClassNotFoundException {
        Set<Class<?>> serviceClasses = new LinkedHashSet<>();
        for (ServiceType serviceType : ServiceType.values()) {
            String serviceClassName = ServiceName.fullClassName(serviceType.getClassName());
            try {
                serviceClasses.add(Class.forName(serviceClassName, false,
                        TableRegistry.class.getClassLoader()));
            } catch (ClassNotFoundException e) {
                throw new ClassNotFoundException("ServiceType 对应的 Service 类不存在: type="
                        + serviceType + ", class=" + serviceClassName, e);
            }
        }
        return new ArrayList<>(serviceClasses);
    }

    /** 注册 ServiceType 中全部 Service 类的 APT 数据库表。 */
    static void registerAllFor(DbTableRegistrar registrar) throws ClassNotFoundException {
        for (Class<?> ownerClass : allServiceClasses()) {
            if (existsFor(ownerClass)) {
                loadFor(ownerClass).register(registrar);
            }
        }
    }

    /** 返回 APT 注册的持久化 DB 表数量，不实例化表对象。 */
    int tableCount();

}
