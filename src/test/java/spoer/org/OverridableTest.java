package spoer.org;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

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

	@Overridable static Day day = Day.Sunday;
	@Test
	public void testEnum() {
		overrideProperties("""
			day=Monday
		""");
		assertEquals(Day.Monday, day);
	}
	enum Day { Sunday, Monday, Tuesday };

	@Overridable static List<Integer> myList = List.of(1, 2, 3);
	@Test
	public void testList() {
		overrideProperties("""
			myList=4, 5
		""");
		assertEquals(List.of(4, 5), myList);
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

	static class Whatever { @Overridable static String map; }
	@Test
	public void testCollision() {
		Exception thrown = assertThrows(Exception.class, () -> Overridable.ConfigFile.validate());
		System.out.println("threw "+thrown);
		assertNotNull(thrown);
	}

	@Overridable String wee;
	@Test
	public void testNonStatic() {
		Exception thrown = assertThrows(Exception.class, () -> Overridable.ConfigFile.validate());
		System.out.println("threw "+thrown);
		assertNotNull(thrown);
	}

	@Overridable static Map<Integer, Double> partialMap = Map.of(1, 1.1, 2, 2.2, 3, 3.3);
	@Disabled
	@Test
	public void testPartialMap() {
		overrideProperties("""
			partialMap.4=4.4
		""");
		assertEquals(Map.of(1, 1.1, 2, 2.2, 3, 3.3, 4, 4.4), partialMap);
	}

	@Overridable.Listener
	private static void onOverridableChange() {
		System.err.println("listened to a property change!");
		//TODO make a real test
	}

	static void overrideProperties(String string) {
		try {
			Files.writeString(Path.of("override.properties"), string);
			Overridable.ConfigFile.overrideAll();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}