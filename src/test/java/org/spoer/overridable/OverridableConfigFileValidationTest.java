package org.spoer.overridable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.spoer.overridable.Overridable.CodeException;
import org.spoer.overridable.Overridable.ConfigFileException;
import org.spoer.overridable.Overridable.FieldIssue;

/*
 * This test is compiled in a separate maven profile, so that it isn't disturbed by the (intentional) incorrect api usage in the other tests.
 */
public class OverridableConfigFileValidationTest {
	@Overridable static int baz = 123;
	@Test
	public void testTypeMismatch() {
		Map<String, Set<FieldIssue>> issues = OverridableConfigFileValidationTest.overrideProperties("""
			baz=qux
		""");
		assertEquals(123, baz);
		assertTrue(issues.get("baz").contains(FieldIssue.FILE_EXCEPTION));
	}

	static Map<String, Set<FieldIssue>> overrideProperties(String string) {
		try {
			Files.writeString(Path.of("target","test-classes","override.properties"), string);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		try {
			Overridable.ConfigFile.optimizeBySelectingFromClasspath(x->x.matches(".*test-classes")); //give this feature some easy coverage
			// Overridable.ConfigFile.optimizeBySelectingFromClasspath(x->false); //swap previous line for this to manually test the logging
			Overridable.ConfigFile.overrideAll();
			return Collections.emptyMap();
		} catch (ConfigFileException | CodeException e) {
			return e.issues;
		}
	}
}
