package ch.ubique.uetlib.rewrite;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class MigrateToPublicUetlibTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("ch.ubique.uetlib.MigrateToPublicUetlib");
    }

    @Test
    void combinesCoordinateAndPackageMigrationWithoutPrivateResolution() {
        rewriteRun(
                xml(
                        "<project><dependencies><dependency><groupId>ch.ubique</groupId><artifactId>uetlib-core</artifactId><version>2.7.0</version></dependency></dependencies></project>",
                        "<project><dependencies><dependency><groupId>ch.ubique.uetlib</groupId><artifactId>uetlib-core</artifactId><version>3.0.0</version></dependency></dependencies></project>",
                        spec -> spec.path("pom.xml")),
                java(
                        "package ch.ubique.backend.example; class Example {}",
                        "package ch.ubique.uetlib.example; class Example {}"));
    }
}
