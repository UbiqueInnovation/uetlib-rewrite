# uetlib-rewrite

Versioned OpenRewrite migration recipes for [uetlib](https://github.com/UbiqueInnovation/uetlib).

Maven coordinates: `ch.uetlib:uetlib-rewrite`.

## Recipes

| Recipe | Purpose |
| --- | --- |
| `ch.uetlib.MigratePackages` | Move Java packages, class-name strings and resource registrations while preserving property keys. |
| `ch.uetlib.rewrite.MigrateConfigurationProperties` | Migrate known configuration keys, Java property references, placeholders and environment-variable names. |
| `ch.uetlib.MigrateToPublicUetlib` | Also migrate configuration keys and Maven parents, BOMs and dependencies to the documented public release versions. |

The package move is part of the upcoming public major releases. These recipes are not published yet; run them only when the target library releases are available.

The package recipe changes Java declarations, imports, qualified references and source paths. It also renames your application's own Java packages under `ch.ubique.backend`; inspect the diff before accepting it. Known class-name strings, scanning annotations, service registrations, Spring imports and protobuf Java-package options are migrated. The package-only recipe preserves property keys; the combined migration also runs the configuration-property recipe. Review dynamic reflection, Kotlin, persisted class names and JVM module/native-access flags separately.

## Module ownership moves

The package recipe also relocates the 99 known production types listed in
[`type-migrations.properties`](src/main/resources/ch/uetlib/rewrite/type-migrations.properties)
and their public nested types. It handles both `ch.ubique.backend.*` and the intermediate
`ch.ubique.uetlib.*` names, including imports, source paths, supported reflection
strings and Spring/service registrations. Core image helpers move under `core.image`,
Spring Boot helpers/security/validation under `spring.boot`, profile test helpers
under `spring.boot.test`, and Spring Cloud integrations under `spring.cloud`.

These are class-specific moves: unrelated core utilities and test helpers remain
in their current packages. Geo integration packages now use
`geo.db` and `geo.job`, Spring storage uses `storage.spring.boot`, and standalone
WebP uses `webp`. Unambiguous scanning roots
such as `security`, `cloud` and `test.enabledif` are updated. Review broad scans and
logger categories rooted in shared `core`, `test` or `image.webp` packages manually;
a single package string cannot express their split across artifacts. Existing
The combined migration also updates known configuration-property names.

## Try locally

Use JDK 25 or newer and Maven 3.9 or newer to build and run these recipes. This is the JVM requirement for the migration tooling; the recipes do not change the consuming project's Java target.

Build locally:

```sh
mvn clean install
```

Then, from a consumer's Maven project, preview the migration:

```sh
mvn org.openrewrite.maven:rewrite-maven-plugin:6.46.1:dryRun \
  -Drewrite.recipeArtifactCoordinates=ch.uetlib:uetlib-rewrite:0.1.0-SNAPSHOT \
  -Drewrite.activeRecipes=ch.uetlib.MigrateToPublicUetlib
```

Once published, use `ch.uetlib:uetlib-rewrite:RELEASE` with `mvn -U` to resolve the latest non-snapshot recipe, or pin a version for repeatable migrations.

The resource migration includes `application.properties` and other `.properties`, YAML and XML files: class-name values are updated, and the combined migration also updates known configuration keys and placeholders.

Review `target/rewrite/rewrite.patch`. To apply it, replace `:dryRun` with `:run`, then build and test the application. Installing the recipe locally does not publish it to Maven Central.

The combined recipe migrates known Maven coordinates and pins existing version elements to the first public migration releases. This deliberately replaces old version expressions in the affected elements; unrelated properties and dependencies remain unchanged. Versionless managed dependencies remain versionless.

### Ubique-internal parents

Projects outside uetlib move only their `<project><parent>` to the Ubique-internal wrapper parents; uetlib libraries and BOMs move to `ch.uetlib`:

| Parent (`ch.ubique`, `ch.ubique.uetlib` or `ch.uetlib`) | Ubique-internal parent |
| --- | --- |
| `uetlib-parent` | `ch.ubique.internal:ubique-library-parent` |
| `uetlib-spring-boot-starter-parent` | `ch.ubique.internal:ubique-spring-boot-starter-parent` |

Wrapper versions equal the public parent versions they wrap. Original `ch.ubique` parents are pinned to the first migration release (`2026.11.0` and `4.1.1-2026.11.0`); intermediate `ch.ubique.uetlib` parents keep their version. Uetlib's own projects (`ch.ubique`, `ch.ubique.uetlib` or `ch.uetlib` groupId, own or inherited, with a `uetlib-*` artifactId) and the wrapper parents themselves keep the public parents.

### Renovate

Renovate can propose the same coordinate changes. Public uetlib repositories extend `github>UbiqueInnovation/uetlib:renovate-base.json5`, which includes only the public `renovate-migration.json5` rules. Ubique-internal consumers use `renovate-config-backend`, whose `base.json5` defines the internal parent replacements directly and extends the public coordinate migration for runtime dependencies. Those parent rules exclude uetlib and the wrapper repositories.

## Add recipes

Place declarative recipes in `src/main/resources/META-INF/rewrite/` and load them with `recipeFromResources` in tests. Prefer small recipes that future release-specific composite recipes can combine. Keep this project independent of uetlib parents and runtime libraries so it can migrate older consumers.

```sh
mvn spotless:apply
mvn verify
```

## License

Apache License 2.0; see [LICENSE](LICENSE).

The public migration also replaces the former Datatrans model/service dependencies
with `ch.uetlib:uetlib-payment-datatrans:3.0.0` and moves
`ch.ubique.datatrans` Java packages to `ch.uetlib.payment.datatrans.client`.
Datatrans client credentials move to `ch.uetlib.payment.datatrans.client.*`; adapter settings move to `ch.uetlib.payment.datatrans.*`.

## Inotify migration

The combined recipe replaces `ch.ubique:inotify:2.0` with `ch.uetlib:uetlib-inotify-java:3.0.0`,
including dependencies in dependency management. Versionless dependencies remain versionless.
It moves `com.den_4.inotify_java` and its subpackages to `ch.uetlib.inotify`, including imports,
class-name strings, scanning annotations, properties/YAML values and service registrations.
The intermediate `ch.ubique.uetlib.inotify` namespace is also migrated. Inotify has no property-prefix mapping; unrelated property keys are preserved.
Renovate performs the Maven coordinate replacement; use OpenRewrite for Java and resource changes.
The replacement library requires Java 25 and remains Linux-only and LGPL-3.0-or-later.

## Geo Spring Boot integration

The combined recipe renames `uetlib-spring-boot-geo` to `ch.uetlib:uetlib-geo-spring-boot` for
original `ch.ubique` and intermediate `ch.ubique.uetlib` / `ch.uetlib` coordinates. Original coordinates
use the first geo migration release (`4.0.0`); intermediate coordinates retain their version.
Packages and supported class/resource references move from the old `spring.boot.geo` namespace to
`ch.uetlib.geo.spring.boot`. Renovate performs the same Maven artifact rename.

## Configuration migration

The combined recipe includes `ch.uetlib.rewrite.MigrateConfigurationProperties`; it can also be run alone.
Its explicit prefix map is [property-migrations.properties](src/main/resources/ch/uetlib/rewrite/property-migrations.properties).
It updates flat/nested YAML, `.properties` keys, placeholders, Java string references (including bindings,
conditions, `@Value`, scheduling and programmatic lookups), XML configuration, Spring metadata and environment
variable names in scanned configuration files. Values and placeholder defaults remain unchanged.
Unknown prefixes and Java class names are left to their respective migrations. Existing new keys are unchanged.
Review deployment configuration outside the project, dynamically constructed keys, shell scripts and Kotlin manually.

Known Spring Boot artifacts need at least `4.0.1`, and storage Spring Boot needs at least `3.0.1`,
for the new configuration prefixes. The Maven migration upgrades older or unresolved declared versions
for these artifacts even if they already use `ch.uetlib`; versionless declarations stay versionless and
newer versions are retained. Other intermediate artifacts keep their versions as before.
