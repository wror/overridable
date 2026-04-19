package spoer.org.overridable;

import static java.lang.System.getProperty;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.ScanResult;
import spoer.org.overridable.Overridable.ConfigFile;

public class OverrideState {
	private final Path user_specified_file;
	private final Iterable<Collection<FieldInfo>> fields;

	public OverrideState(String user_specified_file) {
		this.user_specified_file = user_specified_file == null ? null : Path.of(user_specified_file);
		this.fields = getNewAnnotatedFields();
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

	private static Iterable<Collection<FieldInfo>> getNewAnnotatedFields() {
		Collection<Collection<FieldInfo>> fields = new ArrayList<>();
		ScanResult scanResult = new ClassGraph().enableFieldInfo().enableAnnotationInfo().ignoreClassVisibility().ignoreFieldVisibility().scan();
		ClassInfoList classes = scanResult.getClassesWithFieldAnnotation(Overridable.class);
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

	//outer type is an Iterable because theoretically we could return a lazy collection, and
	// inner type is a Collection because it makes calling code a little more readable
	public Iterable<Collection<FieldInfo>> getAnnotatedFields() {
		return fields;
	}
}