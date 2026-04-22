package org.spoer.overridable;

import static java.lang.System.Logger.Level.ERROR;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

import org.spoer.overridable.Overridable.CodeException;
import org.spoer.overridable.Overridable.ConfigFileException;
import org.spoer.overridable.Overridable.FieldIssue;

import io.github.classgraph.FieldInfo;
import io.github.classgraph.TypeSignature;

public class Validation {
	private final Logger logger = System.getLogger(getClass().getName());
	private final Map<String, Set<FieldIssue>> codeIssues = new HashMap<>(), fileIssues = new HashMap<>();
	private final HashMap<String, Set<String>> classesForField = new HashMap<>();

	public Properties getProperties(State overrideState) {
		try {
			return overrideState.overrideProperties();
		} catch (IOException e) {
			addIssue(null, e);
			return new Properties();
		}
	}

	void eachField(FieldInfo fieldInfo) {
		if (!fieldInfo.isStatic()) {
			addIssue(fieldInfo, FieldIssue.NOT_STATIC);
		}
		if (fieldInfo.isFinal()) {
			addIssue(fieldInfo, FieldIssue.FINAL);
		}
		classesForField.computeIfAbsent(fieldInfo.getName(), k->new HashSet<>()).add(fieldInfo.getClassName());
	}

	@FunctionalInterface interface StringFunction<T> { T apply(String s) throws ReflectiveOperationException; }

	<T> Function<String, T> wrapExceptions(StringFunction<T> f) {
		return s->{
			try {
				return f.apply(s.trim());
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		};
	}

	public Function<String, ?> addIssueAndGetNullDeserializer(FieldInfo fieldInfo, TypeSignature type) {
		RuntimeException e = new UnsupportedTypeException("Could not convert from string to "+type);
		addIssue(fieldInfo, e);
 		return s->{throw e;};
	}

	void addIssue(FieldInfo fieldInfo, Throwable exception) {
		if (codeIssues.getOrDefault(fieldInfo.getName(), Collections.emptySet()).contains(FieldIssue.NOT_STATIC)) {
			return;
		}
		Throwable rootCause = rootCause(exception);
		logger.log(ERROR, "Could not override field: {0}, because of {1}", fieldInfo == null ? "" : fieldInfo.getName(), rootCause);
		if (exception instanceof ReflectiveOperationException || exception instanceof UnsupportedTypeException) {
			addIssue(fieldInfo, FieldIssue.CODE_EXCEPTION);
		} else {
			addIssue(fieldInfo, FieldIssue.FILE_EXCEPTION);
		}
	}

	static class UnsupportedTypeException extends RuntimeException {
		UnsupportedTypeException(String msg) { super(msg); }
	}

	private Throwable rootCause(Throwable t) {
		return ((t instanceof RuntimeException || t instanceof InvocationTargetException) && t.getCause() != null ? rootCause(t.getCause()) : t);
	}

	void addIssue(FieldInfo fieldInfo, FieldIssue issue) {
		addIssue(fieldInfo == null ? null : fieldInfo.getName(), issue);
	}

	private void addIssue(String fieldName, FieldIssue issue) {
		Map<String, Set<FieldIssue>> issues = issue == FieldIssue.FILE_EXCEPTION ? fileIssues : codeIssues;
		issues.computeIfAbsent(fieldName, k->EnumSet.noneOf(FieldIssue.class)).add(issue);
	}

	void tryAndInvokeMethod(Method method, Set<FieldInfo> changedFields) {
		try {
			method.trySetAccessible();
			method.invoke(null);
		} catch (RuntimeException | IllegalAccessException | InvocationTargetException e) {
			logger.log(ERROR, "Exception calling listener for fields {0}: {1}", changedFields.stream().map(FieldInfo::getName), e);
			for (FieldInfo fieldInfo : changedFields) {
				addIssue(fieldInfo, FieldIssue.CODE_EXCEPTION);
			}
		}
	}

	void complete() throws CodeException, ConfigFileException {
		for (Entry<String, Set<String>> c : classesForField.entrySet()) {
			if (c.getValue().size() > 1) {
				logger.log(ERROR, "Naming collision for field {0} in classes: {1}", c.getKey(), c.getValue());
				addIssue(c.getKey(), FieldIssue.COLLISION);
			}
		}
		if (!codeIssues.isEmpty()) {
			throw new CodeException(codeIssues);
		}
		if (!fileIssues.isEmpty()) {
			throw new ConfigFileException(fileIssues);
		}
	}

	static class OverridableException extends Exception {
		public final Map<String, Set<FieldIssue>> issues;

		OverridableException(Map<String, Set<FieldIssue>> issues) {
			this.issues = issues;
		}
	}
}
