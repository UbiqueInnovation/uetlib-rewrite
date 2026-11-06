# Changelog

## 0.1.0

- Rename uetlib configuration keys and property references to `ch.uetlib.*`, including Spring bindings, `@Value`, conditions, scheduling expressions and configuration files. Migrate external deployment configuration as well.

- Move `uetlib-spring-boot-geo` into the geo repository as `uetlib-geo-spring-boot`, with packages under `ch.uetlib.geo.spring.boot`; update consumers and migration mappings.

- Migrate the recovered inotify dependency and original `com.den_4.inotify_java` packages, class references and resource registrations to the public inotify library.

- Require Java 25 for building and running recipes; use the Java 25 parser for migration tests and the shared workflows' Java 25 default.

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
