package org.evd.game.runtime.serialize;

import org.evd.game.runtime.support.LogCore;
import org.evd.game.runtime.support.exception.SysException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public final class SerializerRegisterBootstrap {
    private static final String REGISTER_CLASS_NAME = "SerializerRegister";
    private static final String REGISTER_METHOD_NAME = "register";
    private static final String COMMON_REGISTER_PACKAGE = "org.evd.game.common";
    private static final Set<String> REGISTERED_PACKAGES = new HashSet<>();

    private SerializerRegisterBootstrap() {
    }

    public static void registerIfPresent(Class<?> anchorClass) {
        String packageName = anchorClass.getPackageName();
        registerPackage(packageName);
        if (packageName.startsWith("org.evd.game.") && !COMMON_REGISTER_PACKAGE.equals(packageName)) {
            registerPackage(COMMON_REGISTER_PACKAGE);
        }
    }

    private static synchronized void registerPackage(String packageName) {
        if (REGISTERED_PACKAGES.contains(packageName)) {
            return;
        }
        String registerClassName = packageName + "." + REGISTER_CLASS_NAME;
        try {
            Class<?> registerClass = Class.forName(registerClassName);
            Method registerMethod = registerClass.getDeclaredMethod(REGISTER_METHOD_NAME);
            registerMethod.setAccessible(true);
            registerMethod.invoke(null);
            REGISTERED_PACKAGES.add(packageName);
        } catch (ClassNotFoundException e) {
            LogCore.core.info("模块未生成 SerializerRegister，跳过注册: package={}", packageName);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new SysException("SerializerRegister 反射调用失败: class={}", registerClassName, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new SysException("SerializerRegister 执行失败: class={}", registerClassName, cause);
        }
    }
}
