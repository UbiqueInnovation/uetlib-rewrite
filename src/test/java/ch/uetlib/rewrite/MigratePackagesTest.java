package ch.uetlib.rewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class MigratePackagesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("ch.uetlib.MigratePackages");
    }

    @Test
    void migratesIntermediateNamespaceAndClassReferences() {
        rewriteRun(
                java(
                        """
                        package ch.ubique.uetlib.core;
                        public class Example {
                            String type = "ch.ubique.uetlib.core.Example";
                        }
                        """,
                        """
                        package ch.uetlib.core;
                        public class Example {
                            String type = "ch.uetlib.core.Example";
                        }
                        """),
                java(
                        """
                        package example;
                        import ch.ubique.uetlib.core.Example;
                        class Consumer { Example value; }
                        """,
                        """
                        package example;
                        import ch.uetlib.core.Example;
                        class Consumer { Example value; }
                        """));
    }

    @Test
    void movesDeclarationsAndSourcePaths() {
        rewriteRun(
                java(
                        """
                package ch.ubique.backend.core;

                public class Example {}
                """,
                        """
                package ch.uetlib.core;

                public class Example {}
                """,
                        spec ->
                                spec.path("src/main/java/ch/ubique/backend/core/Example.java")
                                        .afterRecipe(
                                                source ->
                                                        org.junit.jupiter.api.Assertions
                                                                .assertEquals(
                                                                        "src/main/java/ch/uetlib/core/Example.java",
                                                                        source.getSourcePath()
                                                                                .toString()))));
    }

    @Test
    void movesImportsAndQualifiedReferences() {
        rewriteRun(
                java(
                        """
                        package ch.ubique.backend.core;
                        public class Example {}
                        """,
                        """
                        package ch.uetlib.core;
                        public class Example {}
                        """),
                java(
                        """
                        package example;
                        import ch.ubique.backend.core.Example;
                        class Consumer {
                            Example value;
                            ch.ubique.backend.core.Example qualified;
                        }
                        """,
                        """
                        package example;
                        import ch.uetlib.core.Example;
                        class Consumer {
                            Example value;
                            ch.uetlib.core.Example qualified;
                        }
                        """));
    }

    @Test
    void leavesUnrelatedAndAlreadyMigratedPackagesUnchanged() {
        rewriteRun(
                java("package ch.ubique.backendother; class Unrelated {}"),
                java("package ch.uetlib.core; class Migrated {}"),
                java("package example; class Application {}"));
    }
}
