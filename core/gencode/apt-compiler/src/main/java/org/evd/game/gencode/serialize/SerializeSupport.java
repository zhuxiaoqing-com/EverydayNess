package org.evd.game.gencode.serialize;

import freemarker.template.Configuration;
import freemarker.template.Template;
import org.evd.game.base.ISerializable;
import org.evd.game.gencode.AptUtils;
import org.evd.game.gencode.GenConst;
import org.evd.game.gencode.struct.ClassStruct;
import org.evd.game.gencode.struct.FieldStruct;

import java.io.File;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class SerializeSupport {
    static final String CLASS_SUFFIX = "IOSerializer";
    static final String TEMPLATE_SERIALIZE_IO = "IOSerializer.ftl";
    static final String REGISTER_CLASS = "SerializerRegister";
    static final String REGISTER_PACKAGE = "org.evd.game.";
    static final String TEMPLATE_SERIALIZE_REGISTER = "SerializerRegister.ftl";

    private SerializeSupport() {
    }

    static String resolveRegisterPackage(ClassStruct struct) {
        int startIndex = struct.getPackageName().indexOf(REGISTER_PACKAGE);
        int endIndex = struct.getPackageName().indexOf(".", startIndex + REGISTER_PACKAGE.length());
        if (endIndex < 0) {
            endIndex = struct.getPackageName().length();
        }
        return struct.getPackageName().substring(startIndex, endIndex);
    }

    static Map<String, Object> buildRegisterModel(String packageName, List<ClassStruct> structList) {
        Map<String, Object> dataModel = new HashMap<>();
        List<String> importsModel = new ArrayList<>();
        List<Map<String, Object>> fieldInfos = new ArrayList<>();
        List<Map<String, Object>> enumInfos = new ArrayList<>();

        dataModel.put("packageName", packageName);
        dataModel.put("className", REGISTER_CLASS);
        dataModel.put("importPackages", importsModel);
        dataModel.put("fields", fieldInfos);
        dataModel.put("enums", enumInfos);
        for (ClassStruct classStruct : structList) {
            if (classStruct.isAbstract()) {
                continue;
            }
            Map<String, Object> fieldInfo = new HashMap<>();
            if (classStruct.isEnum()) {
                enumInfos.add(fieldInfo);
            } else {
                fieldInfos.add(fieldInfo);
            }

            String fullClassName = classStruct.getFullClassName();
            int hashCode = fullClassName.hashCode();
            fieldInfo.put("key", String.valueOf(hashCode));
            fieldInfo.put("registerName", toRegisterName(fullClassName));
            if (!classStruct.isEnum()) {
                fieldInfo.put("serializerFullName", fullClassName + CLASS_SUFFIX);
                fieldInfo.put("serializerName", classStruct.getClassName() + CLASS_SUFFIX);
            }
            fieldInfo.put("classFullName", fullClassName);
            fieldInfo.put("className", classStruct.getClassName());
        }

        return dataModel;
    }

    static Map<String, Object> buildIoModel(ClassStruct clazz) {
        Map<String, Object> dataModel = new HashMap<>();
        List<String> importsModel = new ArrayList<>();
        List<Map<String, Object>> fieldInfos = new ArrayList<>();

        if (!clazz.isRecord()) {
            ClassStruct superClass = clazz.getSuperClass();
            if (!superClass.getClassName().equals("Object")) {
                dataModel.put("superClass", superClass.getFullClassName() + CLASS_SUFFIX);
            }
        }
        dataModel.put("packageName", clazz.getPackageName());
        dataModel.put("className", clazz.getClassName());
        dataModel.put("proxyName", clazz.getClassName() + CLASS_SUFFIX);
        dataModel.put("importPackages", importsModel);
        dataModel.put("fields", fieldInfos);
        dataModel.put("customized", clazz.isCustomizedSerialize());
        dataModel.put("isRecord", clazz.isRecord());
        dataModel.put("concrete", !clazz.isAbstract());

        for (FieldStruct fieldStruct : clazz.getFields()) {
            Map<String, Object> field = new LinkedHashMap<>();
            fieldInfos.add(field);
            fillTypeInfo(fieldStruct, field);
            field.put("accessor", clazz.isRecord()
                    ? "instance." + fieldStruct.getName() + "()"
                    : "instance." + ("boolean".equals(field.get("type")) ? "is" : "get")
                            + capitalize(fieldStruct.getName()) + "()");
        }

        return dataModel;
    }

    static String renderTemplate(String templateName, Map<String, Object> rootMap) throws Exception {
        Configuration configuration = new Configuration();
        configuration.setDirectoryForTemplateLoading(new File(GenConst.TEMPLATE_DIR));
        configuration.setEncoding(Locale.getDefault(), "UTF-8");
        Template template = configuration.getTemplate(templateName, "UTF-8");
        StringWriter out = new StringWriter();
        template.process(rootMap, out);
        return out.toString();
    }

    private static String toRegisterName(String fullClassName) {
        return fullClassName.replaceAll("[^A-Za-z0-9]", "_");
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static void fillTypeInfo(FieldStruct field, Map<String, Object> info) {
        String name = field.getName();
        String type = AptUtils.typeToBase(field.getType());
        info.put("name", name);
        info.put("type", type);
        if (field.isPrimitive() || field.isString()) {
            info.put("kind", 1);
        } else if (field.isArray()) {
            info.put("kind", 2);
            String elementType = type.replace("[]", "");
            info.put("elementType", elementType);
            info.put("elementIsPrimary", AptUtils.isPrimary(elementType) || AptUtils.isString(elementType) || AptUtils.isObject(elementType));
        } else if (field.isAssignableFrom(List.class)) {
            info.put("kind", 3);
        } else if (field.isAssignableFrom(Map.class)) {
            info.put("kind", 4);
        } else if (field.isAssignableFrom(Set.class)) {
            info.put("kind", 5);
        } else if (field.isAssignableFrom(ISerializable.class)) {
            info.put("kind", 6);
            info.put("serializeType", field.getType() + CLASS_SUFFIX);
        } else {
            info.put("kind", 7);
        }
    }
}
