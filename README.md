Overridable
===========
Rationale
---------
It's nice to be able to configure java applications without having to rebuild and re-release them.
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
	//whatever else your app does on startup...
}
```
then, at any point, you can override any value using a file `override.properties`:
```properties
port=12345
```
There's little reason to not habitually slap `@Overridable` annotation on most "constants", just in case.

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
  * validating the file without running the app
  * appendAll
  * cleanup

Currenly unsupported
--------------------
* special support for feature flags
  * testing combinations, tracking via jira
  * features to help derisk adding new code and removing old (i.e. everything except the actual feature activation)
  * integration with release processses to gather values configured in envs
* general warning of unplanned field deletion (i.e. developer might not have realized field was still being overridden)
* per-environment configuration
* automatic reload
