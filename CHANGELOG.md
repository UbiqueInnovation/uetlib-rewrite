# Changelog

## 0.1.0 (unreleased)

- Move the `uetlib-parent` and `uetlib-spring-boot-starter-parent` parents of projects outside uetlib to `ch.ubique.internal:ubique-library-parent` and `ch.ubique.internal:ubique-spring-boot-starter-parent`. Uetlib projects keep the public parents, and uetlib libraries/BOMs still move to `ch.ubique.uetlib`.

- Migrate geo DB/job, Spring storage and standalone WebP packages from both original and intermediate namespaces.

- Add explicit module-ownership type migrations, including public nested types, to `MigratePackages` and `MigrateToPublicUetlib`. Support original and intermediate package names without moving unrelated types from shared namespaces.

- Add the combined `MigrateToPublicUetlib` recipe for Maven parents/BOMs/dependencies and Java/resource package references. Preserve configuration keys and unrelated artifacts.
- Add Maven metadata, licensing notices and shared contribution/security guidance.

- Add the `ch.ubique.uetlib.MigratePackages` recipe for the proposed Java package migration.
- Test package declarations, source paths, imports, qualified references and unchanged packages.
