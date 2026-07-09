package ${packageName};

import org.evd.game.runtime.serialize.InputStream;
import org.evd.game.runtime.serialize.OutputStream;
import java.io.IOException;

<#if importPackages??>
<#list importPackages as package>
import ${package};
</#list>
</#if>

public final class ${proxyName}{
	/**
	 * 序列化
	 * @param out 输出流
	 * @param instance 实例
	 */
	public static void write(OutputStream out, ${className} instance) throws IOException {
		<#if customized>
		instance.beforeWrite(out);
		instance.writeTo(out);
		instance.afterWrite(out);
		<#else>
		<#if superClass??>
		${superClass}.write(out, instance);
		</#if>
		<#list fields as field>
<#--		<#assign type=field.typeInfo.type?cap_first>-->
			<#-- 1. 基础类型 -->
			<#if field.kind == 1>
				<#if field.type == "boolean">
		out.write${field.type?cap_first}(instance.is${field.name?cap_first}());
				<#else>
		out.write${field.type?cap_first}(instance.get${field.name?cap_first}());
				</#if>
			<#-- 2. 数组类型 -->
			<#elseif field.kind == 2>
				<#if field.elementIsPrimary>
		out.write${field.elementType?cap_first}Array(instance.get${field.name?cap_first}());
				<#else>
		out.write(instance.get${field.name?cap_first}());
				</#if>
			<#-- 3. List类型 -->
			<#elseif field.kind == 3>
		out.writeList(instance.get${field.name?cap_first}());
			<#-- 4. Map类型 -->
			<#elseif field.kind == 4>
		out.writeMap(instance.get${field.name?cap_first}());
			<#-- 5. Set类型 -->
			<#elseif field.kind == 5>
		out.writeSet(instance.get${field.name?cap_first}());
			<#-- 6. 可序列化结构类型 -->
			<#elseif field.kind == 6>
		${field.type} ${field.name} = instance.get${field.name?cap_first}();
		out.writeNull(${field.name});
		if (${field.name} != null) {
			${field.serializeType}.write(out, ${field.name});
		}
			<#-- 8. Enum类型 -->
			<#elseif field.kind == 8>
		out.writeEnum(instance.get${field.name?cap_first}());
			<#-- 7. Object类型 -->
			<#elseif field.kind = 7>
		out.write(instance.get${field.name?cap_first}());
			</#if>
		</#list>
		</#if>
	}
	
	/**
	 * 反序列化
	 * @param in 输入流
	 * @param instance 实例
	 */
	public static void read(InputStream in, ${className} instance) throws IOException {
		<#if customized>
		instance.beforeRead(in);
		instance.readFrom(in);
		instance.afterRead(in);
		<#else>
		<#if superClass??>
		${superClass}.read(in, instance);
		</#if>
		<#list fields as field>
			<#-- 1. 基础类型 -->
			<#if field.kind == 1>
		instance.set${field.name?cap_first}(in.read${field.type?cap_first}());
			<#-- 2. 数组类型 -->
			<#elseif field.kind == 2>
				<#if field.elementIsPrimary>
		instance.set${field.name?cap_first}(in.read${field.elementType?cap_first}Array());
				<#else>
		instance.set${field.name?cap_first}(in.read());
				</#if>
			<#-- 3. List类型 -->
			<#elseif field.kind == 3>
		instance.set${field.name?cap_first}(in.readList());
			<#-- 4. Map类型 -->
			<#elseif field.kind == 4>
		instance.set${field.name?cap_first}(in.readMap());
			<#-- 5. Set类型 -->
			<#elseif field.kind == 5>
		instance.set${field.name?cap_first}(in.readSet());
			<#-- 6. 可序列化结构类型 -->
			<#elseif field.kind == 6>
		if (in.readNull()) {
			instance.set${field.name?cap_first}(null);
		} else {
			${field.type} ${field.name} = new ${field.type}();
			${field.serializeType}.read(in, ${field.name});
			instance.set${field.name?cap_first}(${field.name});
		}
			<#-- 8. Enum类型 -->
			<#elseif field.kind == 8>
		instance.set${field.name?cap_first}((${field.type})in.readEnum());
			<#-- 7. Object类型 -->
			<#elseif field.kind == 7>
		instance.set${field.name?cap_first}(in.read());
			</#if>
		</#list>
		</#if>
	}
}
