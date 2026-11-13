package ch.uetlib.rewrite;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

@SuppressWarnings("java:S2699")
class MigrateToPublicUetlibTest implements RewriteTest {
    @Override
    public void defaults(final RecipeSpec spec) {
        spec.recipeFromResources("ch.uetlib.MigrateToPublicUetlib");
    }

    @Test
    void combinesCoordinateAndPackageMigrationWithoutPrivateResolution() {
        rewriteRun(
                xml(
                        "<project><parent><groupId>ch.ubique</groupId><artifactId>uetlib-parent</artifactId><version>2026.10.1</version></parent><dependencies><dependency><groupId>ch.ubique</groupId><artifactId>uetlib-core</artifactId><version>2.7.0</version></dependency></dependencies></project>",
                        "<project><parent><groupId>ch.ubique.internal</groupId><artifactId>ubique-library-parent</artifactId><version>2026.11.2</version></parent><dependencies><dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-core</artifactId><version>3.0.1</version></dependency></dependencies></project>",
                        spec -> spec.path("pom.xml")),
                java(
                        "package ch.ubique.backend.example; class Example {}",
                        "package ch.uetlib.example; class Example {}"));
    }

    @Test
    void movesInternalConsumersToInternalParentsAndPublicLibraries() {
        rewriteRun(
                xml(
                        "<project><parent><groupId>ch.ubique</groupId><artifactId>uetlib-spring-boot-starter-parent</artifactId><version>4.1.1-2026.10.3</version></parent><artifactId>example-ws</artifactId><dependencies><dependency><groupId>ch.ubique</groupId><artifactId>uetlib-self-bom</artifactId><version>5.7.0</version><type>pom</type><scope>import</scope></dependency></dependencies></project>",
                        "<project><parent><groupId>ch.ubique.internal</groupId><artifactId>ubique-spring-boot-starter-parent</artifactId><version>4.1.2-2026.11.4</version></parent><artifactId>example-ws</artifactId><dependencies><dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-self-bom</artifactId><version>6.0.6</version><type>pom</type><scope>import</scope></dependency></dependencies></project>",
                        spec -> spec.path("pom.xml")));
    }
}
