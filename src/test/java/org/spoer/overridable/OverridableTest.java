package org.spoer.overridable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.spoer.overridable.Overridable.CodeException;
import org.spoer.overridable.Overridable.ConfigFileException;
import org.spoer.overridable.Overridable.FieldIssue;;

public class OverridableTest {

	@Overridable static String foo = "foo is the default";
	@Test
	public void testSimple() throws IOException {
		overrideProperties("""
			foo=bar
		""");
		assertEquals("bar", foo);
	}

	static String bar = "bar";
	@Test
	public void testUnconfigured() throws IOException {
		overrideProperties("""
			bar=notbar
		""");
		assertEquals("bar", bar);
	}

	@Overridable static int baz = 123;
	@Test
	public void testInt() {
		overrideProperties("""
			baz=456
		""");
		assertEquals(456, baz);
	}

	@Test
	public void testTypeMismatch() {
		baz = 0;
		overrideProperties("""
			baz=qux
		""");
		assertEquals(0, baz);
		//TODO figure how to test the ConfigFileException
	}

	@Overridable static int myFieldToBeReloaded;
	@Test
	public void testReload() {
		overrideProperties("""
			myFieldToBeReloaded=1
		""");
		assertEquals(1, myFieldToBeReloaded);

		overrideProperties("""
			myFieldToBeReloaded=2
		""");
		assertEquals(2, myFieldToBeReloaded);
	}

	@Overridable static int wocka = -1;
	@Test
	public void testIntButNotInFile() {
		overrideProperties("""
		""");
		assertEquals(-1, wocka);
	}

	@Overridable static Day day = Day.Sunday;
	@Test
	public void testEnum() {
		overrideProperties("""
			day=Monday
		""");
		assertEquals(Day.Monday, day);
	}
	enum Day { Sunday, Monday, Tuesday };

	@Overridable static char evenAChar = '\0';
	@Test
	public void testChar() {
		overrideProperties("""
			evenAChar=x
		""");
		assertEquals('x', evenAChar);
	}

	@Overridable static Character orACharacter = '\0';
	@Test
	public void testCharacter() {
		overrideProperties("""
			orACharacter=x
		""");
		assertEquals('x', orACharacter);
	}

	@Overridable static List<Integer> myList = List.of(1, 2, 3);
	@Test
	public void testList() {
		overrideProperties("""
			myList=4, 5
		""");
		assertEquals(List.of(4, 5), myList);
	}

	@Test
	public void testListOverriddenToEmpty() {
		overrideProperties("""
			myList=
		""");
		assertEquals(List.of(), myList);
	}

	@Overridable static List<?> listWithoutGenericParam = List.of(1, 2, 3);
	@Test
	public void testListWithoutGenericParam () {
		CodeException thrown = assertThrows(CodeException.class, () -> Overridable.ConfigFile.overrideAll());
		assertTrue(thrown.issues.get("listWithoutGenericParam").contains(FieldIssue.CODE_EXCEPTION));
	}

	@Overridable static Map<Integer, Double> myMap = Map.of(1, 1.1, 2, 2.2, 3, 3.3);
	@Test
	public void testMap() {
		overrideProperties("""
			myMap=4:4.4, 5:5.5
		""");
		assertEquals(Map.of(4, 4.4, 5, 5.5), myMap);
	}

	@Test
	public void testAppend() throws IOException {
		overrideProperties("""
			map=4:4.4, 5:5.5
			#baz=wacka
		""");
		Overridable.ConfigFile.appendAll();
		//TODO expect!
	}

	@Test
	public void testCleanup() throws IOException {
		Overridable.ConfigFile.appendAll();
		Overridable.ConfigFile.cleanup();
		//TODO expect!
	}

	@Overridable.Listener
	private static void onOverridableChange() {
		System.err.println("listened to a property change!");
		//TODO make a real test
	}

	static class Whatever { @Overridable static String myMap; }
	@Test
	public void testCollision() {
		CodeException thrown = assertThrows(CodeException.class, () -> Overridable.ConfigFile.overrideAll());
		assertTrue(thrown.issues.get("myMap").contains(FieldIssue.COLLISION));
	}

	@Overridable String wee;
	@Test
	public void testNonStatic() {
		CodeException thrown = assertThrows(CodeException.class, () -> Overridable.ConfigFile.overrideAll());
		assertTrue(thrown.issues.get("wee").contains(FieldIssue.NOT_STATIC));
	}

	static Map<String, Set<FieldIssue>> overrideProperties(String string) {
		try {
			Files.writeString(Path.of("target","test-classes","override.properties"), string);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		try {
			Overridable.ConfigFile.overrideAll();
			return Collections.emptyMap();
		} catch (CodeException | ConfigFileException e) {
			return e.issues;
		}
	}
}
