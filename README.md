# Overridable
## Rationale
It's nice to be able to configure java applications without having to rebuild and re-release them.
It's also nice to not have to manage and test more configuration than necessary.
It is possible to have the advantages of both, using `Overridable`.

## Usage
Use regular constants in your code, e.g.
```java
private final static int port = 80;
```
annotate it:
```java
@Overridable private final static int port = 80;
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
Overridable fields must be static. Their visibility doesn't matter. (E.g. they can be private.)
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
`overrideAll()` performs validations related to the code, like checking that overridable fields are static, and that they have no name collisions.
You can also call `Overridable.ConfigFile.validate()` to validate the configuration file. One of the nice things about java annotations is that you can run this validation independently of running your application. This holds for the next features as well...
### Automatic config file maintenance
You can call `Overridable.ConfigFile.appendAll()` to automatically write a comment to `override.properties` for each field which is *not* overridden, so that people supporting the app can conveniently see what's available to be configured.
You can call `Overridable.ConfigFile.cleanup()` to automatically remove lines from `overoverride.properties` which do not correspond to overridable fields.

## Currenly unsupported
* special support for feature flags
  * testing combinations, tracking via jira
  * features to help derisk adding new code and removing old (i.e. everything except the actual feature activation)
  * integration with release processses to gather values configured in envs
* general warning of unplanned field deletion (i.e. developer might not have realized field was still being overridden)
* per-environment configuration
* automatic reload
