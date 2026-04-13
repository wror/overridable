package spoer.org;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.classgraph.BaseTypeSignature;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ClassRefTypeSignature;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.ScanResult;
import io.github.classgraph.TypeArgument;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Overridable {
	
	//TODO annotation for method to run when fields change

	public class ConfigFile {
		private static final String override_filename = "override.properties";

		public static void overrideAll() {
			//TODO config file path in predictable location, independent of cwd
			//TODO enable customizing config file path

			Properties prop = new Properties();
			try (InputStream input = new FileInputStream(override_filename)) {
				prop.load(input);
				input.close();
			} catch (IOException ex) {
				ex.printStackTrace(); //TODO
			}

			for (FieldInfo fieldInfo : getAnnotatedFields()) {
				try {
					Function<String, ?> c = switch (fieldInfo.getTypeSignatureOrTypeDescriptor()) {
						case BaseTypeSignature ts -> s->{
							try {
								return Array.get(Array.newInstance(ts.getType(),1),0).getClass().getConstructor(String.class).newInstance(s);
							} catch (Exception e) {
								throw new RuntimeException(e);
							}
						};
						case ClassRefTypeSignature ts -> s->{
							s = s.trim();
							Class<?> cls = ts.loadClass();
							if (cls == List.class) {
								Function<String, ?> f = sm->factoryMethod(getClass(ts, 0), sm);
								return Arrays.stream(s.split(",")).map(f).collect(Collectors.toList());
							} else if (cls == Map.class) {
								Function<String, ?> keyFunction = sm->factoryMethod(getClass(ts, 0), sm.trim().split(":", 2)[0]);
								Function<String, ?> valueFunction = sm->factoryMethod(getClass(ts, 1), sm.trim().split(":", 2)[1]);
								return Arrays.stream(s.split(",")).collect(Collectors.toMap(keyFunction, valueFunction));
							}
							return factoryMethod(cls, s);
						};
						default -> throw new RuntimeException();
					};
					String fieldName = fieldInfo.getName();
					Field field = fieldInfo.getClassInfo().loadClass().getDeclaredField(fieldName);
					field.trySetAccessible();
					if (prop.containsKey(fieldName)) {
						field.set(null, c.apply(prop.getProperty(fieldName)));
					}
				} catch (NoSuchFieldException |  IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			}
		}

		private static Class<?> getClass(ClassRefTypeSignature ts, int index) {
			TypeArgument typeArgument = ts.getTypeArguments().get(index);
			ClassRefTypeSignature argumentTs = (ClassRefTypeSignature)typeArgument.getTypeSignature();
			Class<?> argumentCls = argumentTs.loadClass();
			return argumentCls;
		}

		private static Object factoryMethod(Class<?> cls, String s) {
			s = s.trim();
			try {
				try {
					return cls.getConstructor(String.class).newInstance(s);
				} catch (NoSuchMethodException noStringConstructor) {
					try {
						return cls.getMethod("valueOf", String.class).invoke(null, s);
					} catch (NoSuchMethodException andNoObviousStringFactoryMethod) {
						return null;
					}
				}
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new RuntimeException(e);
			}
		}

		private static Iterable<FieldInfo> getAnnotatedFields() {
			List<FieldInfo> fields = new ArrayList<>();
			ScanResult scanResult = new ClassGraph()
					.enableFieldInfo().enableAnnotationInfo().ignoreClassVisibility().ignoreFieldVisibility().scan();
			ClassInfoList classes = scanResult.getClassesWithFieldAnnotation(Overridable.class);
			for (ClassInfo classInfo : classes) {
				for (FieldInfo fieldInfo : classInfo.getFieldInfo()) {
					if (fieldInfo.hasAnnotation(Overridable.class)) {
						fields.add(fieldInfo);
					}
				}
			}
			return fields;
		}

		public static void validate() {
			Map<String, String> names = new HashMap<>();
			for (FieldInfo fieldInfo : getAnnotatedFields()) {
				if (!fieldInfo.isStatic()) {
					throw new RuntimeException("@Overridable field must be static: "+fieldInfo.getName()+" in "+fieldInfo.getClassName()); //TODO for now
				}
				String thisClass = fieldInfo.getClassName();
				String otherClass = names.put(fieldInfo.getName(), thisClass);
				if (otherClass != null) {
					throw new RuntimeException("More than one @Overridable field with the same name: "+thisClass+" and "+otherClass);
				}
			}
			//TODO actually invoke the above validations from overrideAll()
			//TODO show all the issues
			//TODO type conversion trouble
			//TODO warn re: no java field for property
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		public static void appendAll() throws IOException {
			Map<String, String> overridables = new HashMap<>();
			for (FieldInfo fieldInfo : getAnnotatedFields()) {
				String stringValue = "";
				try {
					Field field = fieldInfo.getClassInfo().loadClass().getDeclaredField(fieldInfo.getName());
					Object value = field.get(null);
					stringValue = switch (value) {
						case Map m -> String.join(", ", (List)m.keySet().stream().map(k->k+":"+m.get(k)).collect(Collectors.toList()));
						case List list -> String.join(", ", list);
						default -> String.valueOf(value);
					};
				} catch (Exception e) {
					//can be run outside of the proper app, in which we don't expect good initialization
				}
				overridables.put(fieldInfo.getName(), stringValue);
			}
			try (InputStream input = new FileInputStream(override_filename)) {
				Properties prop = new Properties();
				prop.load(input);
				prop.stringPropertyNames().forEach(s->overridables.remove(s));
				input.close();
			}
			List<String> linesToWrite = new ArrayList<>();
			try (Stream<String> lines = Files.lines(Path.of(override_filename))) {
				lines.filter(s->!s.startsWith("#") || !s.contains("=")).forEach(linesToWrite::add);
			}
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(override_filename))) {
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
			//TODO one day maybe sort 'em
		}

		public static void cleanup() throws IOException {
			Set<String> overridables = new HashSet<>();
			for (FieldInfo fieldInfo : getAnnotatedFields()) {
				overridables.add(fieldInfo.getName());
			}
			List<String> linesToWrite = new ArrayList<>();
			//TODO '\' line continuation support?
			try (Stream<String> lines = Files.lines(Path.of(override_filename))) {
				//TODO '\' escaping whitespace and = and :
				Pattern propertyName = Pattern.compile("[\\t ]*#?[\\t ]*([^\\t =:]+)=.*");
				lines.forEach(line->{
					Matcher matcher = propertyName.matcher(line);
					if (!matcher.matches() || overridables.contains(matcher.group(1))) {
						linesToWrite.add(line);
					}
				});
				lines.close();
			}
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(override_filename))) {
				for (String line : linesToWrite) {
					writer.write(line);
					writer.newLine();
				}
				writer.close();
			}
		}
	}
}
