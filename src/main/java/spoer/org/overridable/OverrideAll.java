package spoer.org.overridable;

import static java.lang.System.Logger.Level.ERROR;
import static spoer.org.overridable.Overridable.FieldIssue.COLLISION;
import static spoer.org.overridable.Overridable.FieldIssue.CODE_EXCEPTION;
import static spoer.org.overridable.Overridable.FieldIssue.FILE_EXCEPTION;
import static spoer.org.overridable.Overridable.FieldIssue.TYPE_MISMATCH;
import static spoer.org.overridable.Overridable.FieldIssue.UNSUPPORTED_TYPE;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.classgraph.BaseTypeSignature;
import io.github.classgraph.ClassRefTypeSignature;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.TypeArgument;
import spoer.org.overridable.Overridable.CodeException;
import spoer.org.overridable.Overridable.ConfigFileException;
import spoer.org.overridable.Overridable.FieldIssue;

public class OverrideAll {
	private final Logger logger = System.getLogger(getClass().getName());
	private final OverrideState overrideState;
	private final Map<String, Set<FieldIssue>> codeIssues = new HashMap<>(), fileIssues = new HashMap<>();

	public OverrideAll(OverrideState overrideState) {
		this.overrideState = overrideState;
	}

	public void overrideAll() throws CodeException, ConfigFileException {
		Properties properties;
		try {
			properties = overrideState.overrideProperties();
		} catch (IOException e) {
			properties = new Properties();
			addIssue(null, FILE_EXCEPTION, e);
		}
		HashMap<String, Set<String>> classesForField = new HashMap<>();
		for (Collection<FieldInfo> fieldInfos : overrideState.getAnnotatedFields()) {
			Class<?> containingCls = null;
			if (!fieldInfos.isEmpty()) {
				containingCls = fieldInfos.iterator().next().getClassInfo().loadClass();
			}
			Set<FieldInfo> changedFields = new HashSet<>();
			for (FieldInfo fieldInfo : fieldInfos) {
				if (!fieldInfo.isStatic()) {
					addIssue(fieldInfo, FieldIssue.NOT_STATIC);
					continue;
				}
				if (fieldInfo.isFinal()) {
					addIssue(fieldInfo, FieldIssue.FINAL);
					continue;
				}
				classesForField.computeIfAbsent(fieldInfo.getName(), k->new HashSet<>()).add(fieldInfo.getClassName());
				String fieldName = fieldInfo.getName();
				Function<String, ?> deserializer = null;
				switch (fieldInfo.getTypeSignatureOrTypeDescriptor()) {
					case BaseTypeSignature ts:
						Class<? extends Object> wrapperClass = Array.get(Array.newInstance(ts.getType(),1),0).getClass(); //standard one-line hack to get wrapper type for primitve type
						deserializer = singleDeserializer(fieldInfo, wrapperClass);
						break;
					case ClassRefTypeSignature ts:
						try {
							Class<?> cls = ts.loadClass();
							if (cls == List.class) {
								Function<String, ?> memberDeserializer = singleDeserializer(fieldInfo, getClassFromGeneric(ts, 0));
								deserializer = s->Arrays.stream(split(s)).map(memberDeserializer).collect(Collectors.toList());
							} else if (cls == Map.class) {
								Function<String, ?> keyDeserializer = onStringPair(singleDeserializer(fieldInfo, getClassFromGeneric(ts, 0)), 0);
								Function<String, ?> valueDeserializer = onStringPair(singleDeserializer(fieldInfo, getClassFromGeneric(ts, 1)), 1);
								deserializer = s->Arrays.stream(split(s)).collect(Collectors.toMap(keyDeserializer, valueDeserializer));
							} else {
								deserializer = singleDeserializer(fieldInfo, cls);
							}
						} catch (RuntimeException e) {
						}
					default:
				}
				if (deserializer == null) {
					addIssue(fieldName, UNSUPPORTED_TYPE);
					continue;
				}
				try {
					Field field = containingCls.getDeclaredField(fieldName);
					field.trySetAccessible();
					Object originalValue = field.get(null);
					/*
					* We do as much as we can before looking at a value from the config file, 
					* so that we can detect and report all issues that are independent of the config file.
					*/
					if (properties.containsKey(fieldName)) {
						Object newValue = deserializer.apply(properties.getProperty(fieldName));
						if (!Objects.equals(originalValue, newValue)) {
							changedFields.add(fieldInfo);
						}
						field.set(null, newValue);
					}
				} catch (RuntimeException | NoSuchFieldException | IllegalAccessException e) {
					addIssue(fieldInfo, null, e);
					continue;
				}
			}
			if (!changedFields.isEmpty()) {
				for (Method method : containingCls.getDeclaredMethods()) {
					if (method.isAnnotationPresent(Overridable.Listener.class)) {
						try {
							method.trySetAccessible();
							method.invoke(null);
						} catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
							logger.log(ERROR, "Calling listener for fields {}", changedFields.stream().map(FieldInfo::getName), e);
							for (FieldInfo fieldInfo : changedFields) {
								addIssue(fieldInfo, CODE_EXCEPTION);
							}
						}
					}
				}
			}
		}
		for (Entry<String, Set<String>> c : classesForField.entrySet()) {
			if (c.getValue().size() > 1) {
				logger.log(ERROR, "Naming collision for field {} in classes: {}", c.getKey(), c.getValue());
				addIssue(c.getKey(), COLLISION);
			}
		}
		if (!codeIssues.isEmpty()) {
			throw new CodeException(codeIssues);
		}
		if (!fileIssues.isEmpty()) {
			throw new CodeException(fileIssues);
		}
	}

	private <T> Function<String, T> onStringPair(Function<String, T> deserializer, int i) {
		return s->deserializer.apply(s.trim().split(":", 2)[i]);
	}

	private String[] split(String s) {
		return s.trim().isEmpty() ? new String[0] : s.split(",");
	}

	private Class<?> getClassFromGeneric(ClassRefTypeSignature ts, int index) {
		TypeArgument typeArgument = ts.getTypeArguments().get(index);
		ClassRefTypeSignature argumentTs = (ClassRefTypeSignature)typeArgument.getTypeSignature();
		Class<?> argumentCls = argumentTs.loadClass();
		return argumentCls;
	}

	@FunctionalInterface interface StringFunction<T> { T apply(String s) throws IllegalAccessException, InstantiationException, InvocationTargetException; }

	private <T> Function<String, T> singleDeserializer(FieldInfo fieldInfo, Class<T> cls) {
		try {
			StringFunction<T> f;
			try {
				Constructor<T> constructor = cls.getConstructor(String.class);
				f = s->constructor.newInstance(s);
			} catch (NoSuchMethodException noStringConstructor) {
				try {
					Method method = cls.getMethod("valueOf", String.class);
					f = s->(T)method.invoke(null, s);
				} catch (NoSuchMethodException andNoObviousStringFactoryMethod) {
					Constructor<T> constructor = cls.getConstructor(char.class);
					f = s->constructor.newInstance(s.charAt(0));
				}
			}
			final StringFunction<T> finalF = f;
			return s->{
				try {
					return finalF.apply(s.trim());
				} catch (IllegalAccessException | InstantiationException | InvocationTargetException | RuntimeException e) { //RuntimeException only just in case
					addIssue(fieldInfo, null, e);
					throw new RuntimeException(e);
				}
			};
		} catch (NoSuchMethodException noConstructorAtAll) {
			addIssue(fieldInfo, UNSUPPORTED_TYPE);
			throw new RuntimeException("No string constructor for "+cls);
		}
	}

	private void addIssue(FieldInfo fieldInfo, FieldIssue issue, Exception exception) {
		if (fieldInfo != null) {
			logger.log(ERROR, "Could not override field {0}", fieldInfo.getName(), exception);
		} else {
			logger.log(ERROR, "Could not override any fields", exception);
		}
		if (exception instanceof IllegalArgumentException || exception instanceof IllegalAccessException || exception instanceof InstantiationException) {
			issue = CODE_EXCEPTION;
		} else if (exception instanceof InvocationTargetException || exception instanceof RuntimeException) {
			issue = FILE_EXCEPTION;
		}
		addIssue(fieldInfo, issue);
	}

	private void addIssue(FieldInfo fieldInfo, FieldIssue issue) {
		addIssue(fieldInfo == null ? null : fieldInfo.getName(), issue);
	}

	private void addIssue(String fieldName, FieldIssue issue) {
		Map<String, Set<FieldIssue>> issues = EnumSet.of(FILE_EXCEPTION, TYPE_MISMATCH).contains(issue) ? fileIssues : codeIssues;
		issues.computeIfAbsent(fieldName, k->EnumSet.noneOf(FieldIssue.class)).add(issue);
	}

	static class OverridableException extends Exception {
		public final Map<String, Set<FieldIssue>> issues;

		OverridableException(Map<String, Set<FieldIssue>> issues) {
			this.issues = issues;
		}
	}
}
