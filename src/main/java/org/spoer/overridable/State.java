package org.spoer.overridable;

import static java.lang.System.getProperty;
import static java.lang.System.Logger.Level.WARNING;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.System.Logger;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import org.spoer.overridable.Overridable.ConfigFile;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassGraph.ClasspathElementFilter;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.ScanResult;

public class State {
	private final Logger logger = System.getLogger(getClass().getName());
	private final Path user_specified_file;
	private final Iterable<Collection<FieldInfo>> fields;

	public State(String user_specified_file, ClasspathElementFilter classpathElementFilter) {
		this.user_specified_file = user_specified_file == null ? null : Path.of(user_specified_file);
		this.fields = getNewAnnotatedFields(classpathElementFilter);
	}

	public BufferedWriter overrideFileWriter() throws IOException {
		File overrideFile = null;
		if (user_specified_file != null) {
			overrideFile = user_specified_file.toFile();
		} else {
			URL overrideFileURL = Overridable.class.getClassLoader().getResource(ConfigFile.default_filename);
			if (overrideFileURL != null) {
				if (!overrideFileURL.getProtocol().equals("file")) { 
					throw new CannotWriteDefaultFile(overrideFileURL+" is not a regular file");
				}
				try {
					overrideFile = new File(overrideFileURL.toURI());
				} catch (URISyntaxException e) {
					throw new RuntimeException(e);
				}
			} else {
				String classpath[] = getProperty("java.class.path").split(getProperty("path.separator"));
				if (classpath.length > 0) {
					if (!Files.isDirectory(Path.of(classpath[0]))) {
						throw new CannotWriteDefaultFile("First classpath entry \""+classpath[0]+"\" is not a directory");
					}
					overrideFile = Path.of(classpath[0], ConfigFile.default_filename).toFile();
				}
			}
		}
		return new BufferedWriter(new FileWriter(overrideFile));
	}

	class CannotWriteDefaultFile extends RuntimeException { //TODO checked?
		CannotWriteDefaultFile(String message) {
			super(message);
		}
	}

	public BufferedReader overrideFileReader() throws IOException {
		InputStream input = null;
		if (user_specified_file != null) {
			if (Files.exists(Path.of(ConfigFile.default_filename))) {
				input = new FileInputStream(user_specified_file.toFile());
			}
		} else {
			input = Overridable.class.getClassLoader().getResourceAsStream(ConfigFile.default_filename);
		}
		return new BufferedReader(input == null ? Reader.nullReader() : new InputStreamReader(input));
	}

	public Properties overrideProperties() throws IOException {
		Properties properties = new Properties();
		Reader reader = overrideFileReader();
		properties.load(reader);
		reader.close();
		return properties;
	}

	private Iterable<Collection<FieldInfo>> getNewAnnotatedFields(ClasspathElementFilter classpathElementFilter) {
		Collection<Collection<FieldInfo>> fields = new ArrayList<>();
		ClassGraph classGraph = new ClassGraph().enableFieldInfo().enableAnnotationInfo().ignoreClassVisibility().ignoreFieldVisibility();
		LoggingClasspathElementFilter decoratedClasspathElementFilter = new LoggingClasspathElementFilter(classpathElementFilter);
		if (classpathElementFilter != null) {
			classGraph.filterClasspathElements(decoratedClasspathElementFilter);
		}
		ScanResult scanResult = classGraph.scan();
		ClassInfoList classes = scanResult.getClassesWithFieldAnnotation(Overridable.class);
		if (classes.isEmpty()) {
			logger.log(WARNING, "Found no fields annotated with @"+Override.class.getSimpleName());
			decoratedClasspathElementFilter.log();
		}
		for (ClassInfo classInfo : classes) {
			Collection<FieldInfo> fieldsForClass = new ArrayList<>();
			for (FieldInfo fieldInfo : classInfo.getFieldInfo()) {
				if (fieldInfo.hasAnnotation(Overridable.class)) {
					fieldsForClass.add(fieldInfo);
				}
			}
			fields.add(fieldsForClass);
		}
		return fields;
	}

	class LoggingClasspathElementFilter implements ClasspathElementFilter {
		private final ClasspathElementFilter underlying;
		private final List<String> classpathElements = new ArrayList<>();
		private boolean atLeastOneFilteredIn;

		LoggingClasspathElementFilter(ClasspathElementFilter underlying) {
			this.underlying = underlying;
		}

		void log() {
			if (underlying != null && !atLeastOneFilteredIn) {
				logger.log(WARNING, "Since you are using optimizeBySelectingFromClasspath(), you probably want its param to return true for at least one of: "+classpathElements);
			}
		}

		@Override
		public boolean includeClasspathElement(String classpathElementPathStr) {
			boolean includeClasspathElement = underlying.includeClasspathElement(classpathElementPathStr);
			if (includeClasspathElement) {
				atLeastOneFilteredIn = true;
			}
			classpathElements.add(classpathElementPathStr);
			return includeClasspathElement;
		}
	}

	//outer type is an Iterable because theoretically we could return a lazy collection, and
	// inner type is a Collection because it makes calling code a little more readable
	public Iterable<Collection<FieldInfo>> getAnnotatedFields() {
		return fields;
	}
}