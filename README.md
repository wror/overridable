# Overridable
## Rationale
It's nice to be able to configure java applications without having to rebuild and re-release them.
It's also nice to not have to manage and test more configuration than necessary.
It is possible to have the advantages of both, using `Overridable`.

## Usage
Use regular constants in your code, e.g.
```java
private static int port = 80;
```
annotate them:
```java
@Overridable private static int port = 80;
```
and at application startup, initialize once for all:

```java
void main() {
	Overridable.ConfigFile.overrideAll();
	//whatever else your app does on startup...
}
```
then, at any point, you can override any value using a file `override.properties`:
```properties
port=12345
```
There's little reason to not habitually slap `@Overridable` annotation on most "constants", just in case.

## Supported fields and types
It works for enums, strings, primitive types and their wrappers (e.g. double and Double). It works for any type with a constructor that takes a string. And it works for List and Map of any of those. E.g.
```java
@Overridable
static Map<TimeUnit, Long> times = Map.of(TimeUnit.HOURS, 24);
```
And override with:
```properties
times = SECONDS:3, MINUTES:5
```
Overridable fields must be static. They must not be final. Their visibility doesn't matter. (E.g. they can be private.)
## Other features
### Reloading
You can run the `overrideAll()` method again at any time to pick up updates to the `override.properties` file. All your fields will be updated for the next time they're used. Or you can register a listener to be called when the configuration changes, for the fields in the same class as the fields, e.g.
```java
class Foo {
 @Overridable String bar;
}

class Baz {
 @Overridable String port;

 @Overridable.Listener configChange() { //called when port is changed, but not when bar is changed
  reconnect(port);
 }
}
```
### Validating
`overrideAll()` performs validations related to both the override file and the code, like checking that overridable fields are static, and that they have no name collisions. If there’s an issue overriding any fields, it overrides all the fields that it can, and also reports all the issues. So you can choose to interrupt your whole flow, or to just rely on the initial values configured in code and proceed.

One of the nice things about java annotations here is that you can validate configuration independently of running your application. This holds for the next features as well...
### Automatic config file maintenance
You can call `Overridable.ConfigFile.appendAll()` to automatically write a comment to `override.properties` for each field that is *not* overridden, so that people supporting the app can conveniently see what's configurable.
You can call `Overridable.ConfigFile.cleanup()` to automatically remove lines from `override.properties` that do not correspond to overridable fields.

## Currenly unsupported
* special support for feature flags
  * automated testing of field combinations
  * integration with release processses to gather values configured in envs
* general warning of unplanned field deletion (i.e. developer might not have realized field was still being overridden)
* per-environment configuration in code
* automatic reload
* multiline and escape character properties file support
* configuring fields of final types that have with no string constructors (which'd be a problem if you can't change them)
