Rationale
---------
It's nice to be able to configure applications without having to rebuild and re-release them.
It's also nice to not have to manage and test more configuration than necessary.
It is possible to have the advantages of both, using `Overridable`.

Usage
-----

Use regular constants in your code, e.g.
```java
private final static int port = 80;
```
and add a new annotation:
```java
@Overridable private final static int port = 80;
```
and at application startup, initialize `Overridable` once for all:

```java
void main() {
	Overridable.ConfigFile.overrideAll();
	//whatever else your app does...
}
```
then, at any point, you can override any value using a file `override.properties`:
```properties
port=12345
```
There's little reason to not just slap `@Overridable` annotation on most "constants", just in case.

Outline of documentation to come
--
* maven dependency
* types
  * inferred from your java field type
  * enums
  * anything with a String constructor
  * list
  * map
* reload
* simplicity enabled by validation
  * unqualified field names in the file, validated for collisions
  * fields must be static
* making `override.properties` easy to maintain
  * validating the file
  * appendAll
  * cleanup

Currenly unsupported
--------------------
* api for handling intraday reloads
* warning of premature field deletion (i.e. still being overridden)
* special feature flag features
  * gui
  * testing combinations, tracking via jira
  * tech debt removal
* per-environment configuration
* automatic reload
