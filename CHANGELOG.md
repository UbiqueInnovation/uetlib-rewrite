# Changelog

## 0.2.1

- Use shared Maven workflows at `v1.1.0`.

## 0.2.0

- Target self-bom `6.0.6`, public starter-parent `4.1.2-2026.11.6` and internal starter-parent `4.1.2-2026.11.4`.
- Pin migrated parents to internal library parent `2026.11.2` and starter-parent `4.1.2-2026.11.4` instead of retaining
  public versions.
- Target inotify `3.0.3` and job `6.0.5` in the Maven coordinate migration.

## 0.1.5

- Use shared Maven build and publish workflows at `v1.0.0` to enforce formatting checks in CI.

## 0.1.4

- Require a supported uetlib library or Spring Boot starter-parent for the entire combined migration, including local
  reactor inheritance; reject unrelated parents before writing any changes. Map these parents to their matching
  Ubique-internal wrappers for consumers outside uetlib.
- Remove plain uetlib dependency-management pins whenever the resolved parent manages the artifact, regardless of version;
  adopt the parent's versions while retaining custom declarations and unmanaged artifacts.

## 0.1.3

- Migrate job queue prerelease dependencies to 6.0.3, including intermediate and public coordinates.
- Cover queue-specific configuration keys, Java references and deployment environment variables; document the removed
  overlap-grace setting.

## 0.1.2

- Align public migration versions with the coordinated Central batch.

- Target `ch.ubique.internal:ubique-spring-boot-starter-parent:4.1.2-2026.11.2` when migrating original Ubique Spring
  Boot parents.

## 0.1.1

- Require Java 25 for recipe execution.
- Migrate known configuration properties in Java, properties, YAML and deployment resources.
- Migrate legacy inotify coordinates and references, and rename the geo Spring Boot integration artifact and packages.
- Update migration versions and add regression coverage.

- Rename uetlib configuration keys and property references to `ch.uetlib.*`, including Spring bindings, `@Value`,
  conditions, scheduling expressions and configuration files. Migrate external deployment configuration as well.

- Move `uetlib-spring-boot-geo` into the geo repository as `uetlib-geo-spring-boot`, with packages under
  `ch.uetlib.geo.spring.boot`; update consumers and migration mappings.

- Migrate the recovered inotify dependency and original `com.den_4.inotify_java` packages, class references and resource
  registrations to the public inotify library.

- Require Java 25 for building and running recipes; use the Java 25 parser for migration tests.

- Move Java packages and class references from `ch.ubique.uetlib` to `ch.uetlib`.

- Move the `uetlib-parent` and `uetlib-spring-boot-starter-parent` parents of projects outside uetlib to
  `ch.ubique.internal:ubique-library-parent` and `ch.ubique.internal:ubique-spring-boot-starter-parent`. Uetlib projects
  keep the public parents, and uetlib libraries/BOMs move to `ch.uetlib`.

- Migrate geo DB/job, Spring storage and standalone WebP packages from both original and intermediate namespaces.

- Add explicit module-ownership type migrations, including public nested types, to `MigratePackages` and
  `MigrateToPublicUetlib`. Support original and intermediate package names without moving unrelated types from shared
  namespaces.

- Add the combined `MigrateToPublicUetlib` recipe for Maven parents/BOMs/dependencies and Java/resource package
  references. Preserve configuration keys and unrelated artifacts.
- Add Maven metadata, licensing notices and shared contribution/security guidance.

- Add the `ch.uetlib.MigratePackages` recipe for the proposed Java package migration.
- Test package declarations, source paths, imports, qualified references and unchanged packages.
