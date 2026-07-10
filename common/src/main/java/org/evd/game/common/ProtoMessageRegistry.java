package org.evd.game.common;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.GeneratedMessage;
import org.evd.game.runtime.serialize.InputStream;
import org.evd.game.runtime.support.exception.SysException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

public final class ProtoMessageRegistry {
    private static final String PROTO_PACKAGE = "org.evd.game.common.proto";
    private static final Class<?>[] PARSE_FROM_ARGS = new Class<?>[]{CodedInputStream.class};

    private ProtoMessageRegistry() {
    }

    public static void register() {
        List<Class<?>> classes = ClassFinder.getAllClass(PROTO_PACKAGE);
        for (Class<?> clazz : classes) {
            if (!GeneratedMessage.class.isAssignableFrom(clazz)) {
                continue;
            }
            if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                continue;
            }
            Method parseMethod = getParseMethod(clazz);
            int messageId = clazz.getName().hashCode();
            InputStream.registerSerializeReadMsgFunc(messageId,
                    clazz, new ReflectiveMsgReader(parseMethod));
        }
    }

    private static final class ReflectiveMsgReader
            implements org.evd.game.runtime.support.function.ReturnFunction2<GeneratedMessage, Integer, CodedInputStream> {
        private final Method parseMethod;

        private ReflectiveMsgReader(Method parseMethod) {
            this.parseMethod = parseMethod;
        }

        @Override
        public GeneratedMessage apply(Integer id, CodedInputStream input) {
            return parseMessage(parseMethod, id, input);
        }
    }

    private static Method getParseMethod(Class<?> clazz) {
        try {
            return clazz.getMethod("parseFrom", PARSE_FROM_ARGS);
        } catch (NoSuchMethodException e) {
            throw new SysException(e, "protobuf 消息缺少 parseFrom(CodedInputStream): class={}", clazz.getName());
        }
    }

    private static GeneratedMessage parseMessage(Method parseMethod,
                                                 int id,
                                                 CodedInputStream input) {
        try {
            return (GeneratedMessage) parseMethod.invoke(null, input);
        } catch (IllegalAccessException e) {
            throw new SysException(e, "protobuf 消息 parseFrom 不可访问: id={}, class={}",
                    id, parseMethod.getDeclaringClass().getName());
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new SysException(cause, "protobuf 消息反序列化失败: id={}, class={}",
                    id, parseMethod.getDeclaringClass().getName());
        }
    }
}
