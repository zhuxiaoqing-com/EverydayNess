package ${packageName};

import org.evd.game.runtime.serialize.InputStream;
import org.evd.game.runtime.serialize.OutputStream;
import java.io.IOException;

<#if importPackages??>
<#list importPackages as package>
import ${package};
</#list>
</#if>

<#macro readField field>
			<#if field.kind == 1>
		${field.type} ${field.name} = in.read${field.type?cap_first}();
			<#elseif field.kind == 2>
				<#if field.elementIsPrimary>
		${field.type} ${field.name} = in.read${field.elementType?cap_first}Array();
				<#else>
		${field.type} ${field.name} = in.read();
				</#if>
			<#elseif field.kind == 3>
		${field.type} ${field.name} = in.readList();
			<#elseif field.kind == 4>
		${field.type} ${field.name} = in.readMap();
			<#elseif field.kind == 5>
		${field.type} ${field.name} = in.readSet();
			<#elseif field.kind == 6>
		${field.type} ${field.name};
		if (in.readNull()) {
			${field.name} = null;
		} else {
			${field.name} = ${field.serializeType}.read(in);
		}
			<#elseif field.kind == 8>
		${field.type} ${field.name} = (${field.type}) in.readEnum();
			<#elseif field.kind == 7>
		${field.type} ${field.name} = in.read();
			</#if>
</#macro>

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
			<#if field.kind == 1>
		out.write${field.type?cap_first}(${field.accessor});
			<#elseif field.kind == 2>
				<#if field.elementIsPrimary>
		out.write${field.elementType?cap_first}Array(${field.accessor});
				<#else>
		out.write(${field.accessor});
				</#if>
			<#elseif field.kind == 3>
		out.writeList(${field.accessor});
			<#elseif field.kind == 4>
		out.writeMap(${field.accessor});
			<#elseif field.kind == 5>
		out.writeSet(${field.accessor});
			<#elseif field.kind == 6>
		${field.type} ${field.name} = ${field.accessor};
		out.writeNull(${field.name});
		if (${field.name} != null) {
			${field.serializeType}.write(out, ${field.name});
		}
			<#elseif field.kind == 8>
		out.writeEnum(${field.accessor});
			<#elseif field.kind == 7>
		out.write(${field.accessor});
			</#if>
		</#list>
		</#if>
	}

	<#if concrete>
	/**
	 * 反序列化并返回实例。
	 * @param in 输入流
	 * @return 反序列化后的实例
	 */
	public static ${className} read(InputStream in) throws IOException {
		<#if isRecord>
		<#list fields as field>
		<@readField field=field />
		</#list>
		return new ${className}(<#list fields as field>${field.name}<#if field_has_next>, </#if></#list>);
		<#else>
		${className} instance = new ${className}();
		readInto(in, instance);
		return instance;
		</#if>
	}
	</#if>

	<#if !isRecord>
	/**
	 * 将输入流内容填充到既有实例，供子类反序列化父类字段使用。
	 * @param in 输入流
	 * @param instance 待填充实例
	 */
	public static void readInto(InputStream in, ${className} instance) throws IOException {
		<#if customized>
		instance.beforeRead(in);
		instance.readFrom(in);
		instance.afterRead(in);
		<#else>
		<#if superClass??>
		${superClass}.readInto(in, instance);
		</#if>
		<#list fields as field>
		<@readField field=field />
		instance.set${field.name?cap_first}(${field.name});
		</#list>
		</#if>
	}
	</#if>
}
