# uetlib-rewrite

Versioned OpenRewrite migration recipes for [uetlib](https://github.com/UbiqueInnovation/uetlib).

Maven coordinates: `ch.uetlib:uetlib-rewrite`.

## Recipes

| Recipe | Purpose |
| --- | --- |
| `ch.ubique.uetlib.MigratePackages` | Move Java packages, class-name strings and resource registrations while preserving property keys. |
| `ch.ubique.uetlib.MigrateToPublicUetlib` | Also migrate Maven parents, BOMs and dependencies to the documented public release versions. |

The package move is part of the upcoming public major releases. These recipes are not published yet; run them only when the target library releases are available.

The package recipe changes Java declarations, imports, qualified references and source paths. It also renames your application's own Java packages under `ch.ubique.backend`; inspect the diff before accepting it. Known class-name strings, scanning annotations, service registrations, Spring imports and protobuf Java-package options are migrated. Existing Spring property keys are preserved. Review dynamic reflection, Kotlin, persisted class names and JVM module/native-access flags separately.

## Module ownership moves

The package recipe also relocates the 99 known production types listed in
[`type-migrations.properties`](src/main/resources/ch/ubique/uetlib/rewrite/type-migrations.properties)
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
Spring configuration-property names remain unchanged.

## Try locally

Use Java 17 and Maven 3.9 or newer:

```sh
mvn clean install
```

Then, from a consumer's Maven project, preview the migration:

```sh
mvn org.openrewrite.maven:rewrite-maven-plugin:6.46.1:dryRun \
  -Drewrite.recipeArtifactCoordinates=ch.uetlib:uetlib-rewrite:0.1.0-SNAPSHOT \
  -Drewrite.activeRecipes=ch.ubique.uetlib.MigrateToPublicUetlib
```

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
`ch.ubique.datatrans` Java packages to `ch.ubique.uetlib.payment.datatrans.client`.
Datatrans credential property names stay unchanged.
