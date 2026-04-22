package org.spoer.overridable;

import java.lang.System.Logger;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.spoer.overridable.Overridable.FieldIssue;

import io.github.classgraph.BaseTypeSignature;
import io.github.classgraph.ClassRefTypeSignature;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.TypeArgument;
import io.github.classgraph.TypeSignature;

public class OverrideAll {
	private final Logger logger = System.getLogger(getClass().getName());
	private final State overrideState;

	public OverrideAll(State overrideState) {
		this.overrideState = overrideState;
	}

	public void overrideAll() throws Overridable.CodeException, Overridable.ConfigFileException {
		Validation valid = new Validation();
		Properties properties = valid.getProperties(overrideState);
		for (Collection<FieldInfo> fieldInfos : overrideState.getAnnotatedFields()) {
			Set<FieldInfo> changedFields = new HashSet<>();
			Class<?> containingCls = null;
			for (FieldInfo fieldInfo : fieldInfos) {
				valid.eachField(fieldInfo);
				try {
					Function<String, ?> deserializer = deserializer(fieldInfo, valid);
					String fieldName = fieldInfo.getName();
					if (containingCls == null) {
						containingCls = fieldInfos.iterator().next().getClassInfo().loadClass();
					}
					Field field = containingCls.getDeclaredField(fieldName);
					field.trySetAccessible();
					Object originalValue = field.get(null);
					/*
					 * We do as much as we can before looking at a value from the config file, 
					 * so that we can detect and report all issues that are independent of the config file.
					 */
					if (properties.containsKey(fieldName)) {
						Object newValue = deserializer.apply(properties.getProperty(fieldName));
						field.set(null, newValue);
						if (!Objects.equals(originalValue, newValue)) {
							changedFields.add(fieldInfo);
						}
					}
				} catch (RuntimeException | IllegalAccessException | NoSuchFieldException e) {
					valid.addIssue(fieldInfo, e);
				}
			}
			if (!changedFields.isEmpty()) {
				for (Method method : containingCls.getDeclaredMethods()) {
					if (method.isAnnotationPresent(Overridable.Listener.class)) {
						valid.tryAndInvokeMethod(method, changedFields);
					}
				}
			}
		}
		valid.complete();
	}

	private Function<String, ?> deserializer(FieldInfo fieldInfo, Validation valid) {
		TypeSignature type = fieldInfo.getTypeSignatureOrTypeDescriptor();
		switch (type) {
			case BaseTypeSignature ts:
				Class<? extends Object> wrapperClass = Array.get(Array.newInstance(ts.getType(),1),0).getClass(); //standard one-line hack to get wrapper type for primitve type
				return constructor(wrapperClass, fieldInfo, valid);
			case ClassRefTypeSignature ts:
				try {
					Class<?> cls = ts.loadClass();
					if (cls == List.class) {
						Function<String, ?> memberDeserializer = constructor(getClassFromGeneric(ts, 0), fieldInfo, valid);
						return s->Arrays.stream(split(s)).map(memberDeserializer).collect(Collectors.toList());
					} else if (cls == Map.class) {
						Function<String, ?> keyDeserializer = onStringPair(constructor(getClassFromGeneric(ts, 0), fieldInfo, valid), 0);
						Function<String, ?> valueDeserializer = onStringPair(constructor(getClassFromGeneric(ts, 1), fieldInfo, valid), 1);
						return s->Arrays.stream(split(s)).collect(Collectors.toMap(keyDeserializer, valueDeserializer));
					} else {
						return constructor(cls, fieldInfo, valid);
					}
				} catch (RuntimeException e) {
					//fall-thru!
				}
			default:
				return valid.addIssueAndGetNullDeserializer(fieldInfo, type);
		}
	}

	private <T> Function<String, T> constructor(Class<T> cls, FieldInfo fieldInfo, Validation valid) {
		try {
			Constructor<T> constructor = cls.getConstructor(String.class);
			return valid.wrapExceptions(s->constructor.newInstance(s));
		} catch (NoSuchMethodException noStringConstructor) {
			try {
				Method method = cls.getMethod("valueOf", String.class);
				return valid.wrapExceptions(s->(T)method.invoke(null, s));
			} catch (NoSuchMethodException andNoObviousStringFactoryMethod) {
				try {
					Constructor<T> constructor = cls.getConstructor(char.class);
					return valid.wrapExceptions(s->constructor.newInstance(s.charAt(0)));
				} catch (NoSuchMethodException andNoCharConstructor) {
					valid.addIssue(fieldInfo, FieldIssue.UNSUPPORTED_TYPE);
					throw new RuntimeException("No string constructor for "+cls);
				}
			}
		}
	}

	private Class<?> getClassFromGeneric(ClassRefTypeSignature ts, int index) {
		TypeArgument typeArgument = ts.getTypeArguments().get(index);
		ClassRefTypeSignature argumentTs = (ClassRefTypeSignature)typeArgument.getTypeSignature();
		Class<?> argumentCls = argumentTs.loadClass();
		return argumentCls;
	}

	private <T> Function<String, T> onStringPair(Function<String, T> deserializer, int i) {
		return s->deserializer.apply(s.trim().split(":", 2)[i]);
	}

	private String[] split(String s) {
		return s.trim().isEmpty() ? new String[0] : s.split(",");
	}
}