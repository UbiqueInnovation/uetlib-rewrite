# uetlib-rewrite

Versioned OpenRewrite migration recipes for [uetlib](https://github.com/UbiqueInnovation/uetlib).

Maven coordinates: `ch.ubique.uetlib:uetlib-rewrite`.

## Recipes

| Recipe | Purpose |
| --- | --- |
| `ch.ubique.uetlib.MigratePackages` | Move Java packages, class-name strings and resource registrations while preserving property keys. |
| `ch.ubique.uetlib.MigrateToPublicUetlib` | Also migrate Maven parents, BOMs and dependencies to the documented public release versions. |

The package move is part of the upcoming public major releases. These recipes are not published yet; run them only when the target library releases are available.

The package recipe changes Java declarations, imports, qualified references and source paths. It also renames your application's own Java packages under `ch.ubique.backend`; inspect the diff before accepting it. Known class-name strings, scanning annotations, service registrations, Spring imports and protobuf Java-package options are migrated. Existing Spring property keys are preserved. Review dynamic reflection, Kotlin, persisted class names and JVM module/native-access flags separately.

## Try locally

Use Java 17 and Maven 3.9 or newer:

```sh
mvn clean install
```

Then, from a consumer's Maven project, preview the migration:

```sh
mvn org.openrewrite.maven:rewrite-maven-plugin:6.46.1:dryRun \
  -Drewrite.recipeArtifactCoordinates=ch.ubique.uetlib:uetlib-rewrite:0.1.0-SNAPSHOT \
  -Drewrite.activeRecipes=ch.ubique.uetlib.MigrateToPublicUetlib
```

Review `target/rewrite/rewrite.patch`. To apply it, replace `:dryRun` with `:run`, then build and test the application. Installing the recipe locally does not publish it to Maven Central.

The combined recipe migrates known Maven coordinates and pins existing version elements to the first public migration releases. This deliberately replaces old version expressions in the affected elements; unrelated properties and dependencies remain unchanged. Versionless managed dependencies remain versionless. Maven coordinate changes can also be proposed by the shared uetlib Renovate preset.

## Add recipes

Place declarative recipes in `src/main/resources/META-INF/rewrite/` and load them with `recipeFromResources` in tests. Prefer small recipes that future release-specific composite recipes can combine. Keep this project independent of uetlib parents and runtime libraries so it can migrate older consumers.

```sh
mvn spotless:apply
mvn verify
```

## License

Apache License 2.0; see [LICENSE](LICENSE).

The public migration also replaces the former Datatrans model/service dependencies
with `ch.ubique.uetlib:uetlib-payment-datatrans:3.0.0` and moves
`ch.ubique.datatrans` Java packages to `ch.ubique.uetlib.payment.datatrans.client`.
Datatrans credential property names stay unchanged.
