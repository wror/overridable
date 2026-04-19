package spoer.org.overridable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.github.classgraph.FieldInfo;

public class Cleanup {
	private OverrideState overrideState;

	public Cleanup(OverrideState overrideState) {
		this.overrideState = overrideState;
	}

	public void cleanup() throws IOException {
		Set<String> overridables = new HashSet<>();
		for (Collection<FieldInfo> fieldInfos : overrideState.getAnnotatedFields()) {
			for (FieldInfo fieldInfo : fieldInfos) {
				overridables.add(fieldInfo.getName());
			}
		}
		List<String> linesToWrite = new ArrayList<>();
		try (Stream<String> lines = overrideState.overrideFileReader().lines()) {
			Pattern propertyName = Pattern.compile("[\t ]*#?[\t ]*([^\t =:]+)=.*");
			lines.forEach(line->{
				Matcher matcher = propertyName.matcher(line);
				if (!matcher.matches() || overridables.contains(matcher.group(1))) {
					linesToWrite.add(line);
				}
			});
			lines.close();
		}
		try (BufferedWriter writer = overrideState.overrideFileWriter()) {
			for (String line : linesToWrite) {
				writer.write(line);
				writer.newLine();
			}
			writer.close();
		}
	}
}