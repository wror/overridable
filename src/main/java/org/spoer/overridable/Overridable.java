package org.spoer.overridable;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.Set;

import org.spoer.overridable.Validation.OverridableException;

import io.github.classgraph.ClassGraph.ClasspathElementFilter;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Overridable {

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	@interface Listener {
	}

	class ConfigFile {
		private static State overrideState;
		private static ClasspathElementFilter classpathElementFilter;
		private static String filename; //on the filesystem
		public static final String default_filename = "override.properties"; //in the classpath

		public static void optimizeBySelectingFromClasspath(ClasspathElementFilter classpathElementFilterArg) {
			if (overrideState != null) {
				throw new RuntimeException("set before using overrideAll, appendAll, and cleanup");
			}
			classpathElementFilter = classpathElementFilterArg;
		}
			
		public static void set(String filenameArg) {
			if (overrideState != null) {
				throw new RuntimeException("set before using overrideAll, appendAll, and cleanup");
			}
			filename = filenameArg;
		}

		private static State overrideState() {
			if (overrideState == null) {
				overrideState = new State(filename, classpathElementFilter);
			}
			return overrideState;
		}

		public static void overrideAll() throws CodeException, ConfigFileException {
			new OverrideAll(overrideState()).overrideAll();
		}

		public static void appendAll() throws IOException {
			new AppendAll(overrideState()).appendAll();
		}

		public static void cleanup() throws IOException {
			new Cleanup(overrideState()).cleanup();
		}
	}

	/*
	 * Even when we throw an exception, we override all the fields that we can. If there are issues with both the code and the file, we only throw a CodeException.
	 */
	class CodeException extends OverridableException {
		CodeException(Map<String, Set<FieldIssue>> fieldIssues) {
			super(fieldIssues);
		}
	}

	public enum FieldIssue { NOT_STATIC, FINAL, UNSUPPORTED_TYPE, COLLISION, FILE_EXCEPTION, CODE_EXCEPTION }

	class ConfigFileException extends OverridableException {
		ConfigFileException(Map<String, Set<FieldIssue>> fieldIssues) {
			super(fieldIssues);
		}
	}
}
