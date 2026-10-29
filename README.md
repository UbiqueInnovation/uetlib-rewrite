# uetlib-rewrite

Versioned OpenRewrite migration recipes for [uetlib](https://github.com/UbiqueInnovation/uetlib).

Maven coordinates: `ch.ubique.uetlib:uetlib-rewrite`.

## Recipes

| Recipe | Purpose |
| --- | --- |
| `ch.ubique.uetlib.MigratePackages` | Move `ch.ubique.backend` and its subpackages to `ch.ubique.uetlib`. |

The package move is proposed, and this recipe is not published yet. Run it only when upgrading to a uetlib release that actually uses the new Java packages. The Maven groupId change alone does not require it.

The package recipe changes Java declarations, imports, qualified references and source paths. It also renames your application's own Java packages under `ch.ubique.backend`; inspect the diff before accepting it. Review configuration files, reflection strings, service registrations and persisted class names separately. Kotlin and other languages are outside this initial recipe's scope.

## Try locally

Use Java 17 and Maven 3.9 or newer:

```sh
mvn clean install
```

Then, from a consumer's Maven project, preview the migration:

```sh
mvn org.openrewrite.maven:rewrite-maven-plugin:6.46.1:dryRun \
  -Drewrite.recipeArtifactCoordinates=ch.ubique.uetlib:uetlib-rewrite:0.1.0-SNAPSHOT \
  -Drewrite.activeRecipes=ch.ubique.uetlib.MigratePackages
```

Review `target/rewrite/rewrite.patch`. To apply it, replace `:dryRun` with `:run`, then build and test the application. Installing the recipe locally does not publish it to Maven Central.

Use the shared uetlib Renovate preset for the Maven coordinate migration from `ch.ubique:uetlib-*` to `ch.ubique.uetlib:uetlib-*`; the package recipe does not change dependency coordinates or versions.

## Add recipes

Place declarative recipes in `src/main/resources/META-INF/rewrite/` and load them with `recipeFromResources` in tests. Prefer small recipes that future release-specific composite recipes can combine. Keep this project independent of uetlib parents and runtime libraries so it can migrate older consumers.

```sh
mvn spotless:apply
mvn verify
```

## License

Apache License 2.0; see [LICENSE](LICENSE).
