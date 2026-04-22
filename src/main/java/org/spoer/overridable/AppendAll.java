package org.spoer.overridable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.classgraph.FieldInfo;

public class AppendAll {
	private State overrideState;

	public AppendAll(State overrideState) {
		this.overrideState = overrideState;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void appendAll() throws IOException {
		Map<String, String> overridables = new HashMap<>();
		for (Collection<FieldInfo> fieldInfos : overrideState.getAnnotatedFields()) {
			Class<?> containingCls = null;
			for (FieldInfo fieldInfo : fieldInfos) {
				if (containingCls == null) {
					containingCls = fieldInfo.getClassInfo().loadClass();
				}
				String stringValue = "";
				try {
					Field field = containingCls.getDeclaredField(fieldInfo.getName());
					Object value = field.get(null);
					stringValue = switch (value) {
						case Map m -> String.join(", ", (List)m.keySet().stream().map(k->k+":"+m.get(k)).collect(Collectors.toList()));
						case List list -> String.join(", ", list);
						default -> String.valueOf(value);
					};
				} catch (Exception e) {
					//can be run outside of the proper app, in which case we don't expect good initialization
				}
				overridables.put(fieldInfo.getName(), stringValue);
			}
		}
		Properties properties = overrideState.overrideProperties();
		properties.stringPropertyNames().forEach(s->overridables.remove(s));
		List<String> linesToWrite = new ArrayList<>();
		try (Stream<String> lines = overrideState.overrideFileReader().lines()) {
			lines.filter(s->!s.startsWith("#") || !s.contains("=")).forEach(linesToWrite::add);
		}
		try (BufferedWriter writer = overrideState.overrideFileWriter()) {
			for (String line : linesToWrite) {
				writer.write(line);
				writer.newLine();
			}
			for (String name : overridables.keySet()) {
				writer.write("#"+name+"="+overridables.get(name));
				writer.newLine();
			}
			writer.close();
		}
	}
}