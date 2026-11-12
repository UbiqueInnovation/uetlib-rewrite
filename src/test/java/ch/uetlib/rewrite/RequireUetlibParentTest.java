package ch.uetlib.rewrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.RecipeRun;
import org.openrewrite.SourceFile;
import org.openrewrite.config.Environment;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.marker.Markers;
import org.openrewrite.text.PlainText;
import org.openrewrite.xml.XmlParser;

class RequireUetlibParentTest {
    @Test
    void acceptsBothParentFamiliesAndAlreadyMigratedWrappers() {
        for (final var group : List.of("ch.ubique", "ch.ubique.uetlib", "ch.uetlib")) {
            for (final var parent : List.of("uetlib-parent", "uetlib-spring-boot-starter-parent")) {
                assertAccepts(pom("pom.xml", group, parent, "1"));
            }
        }
        for (final var parent :
                List.of("ubique-library-parent", "ubique-spring-boot-starter-parent")) {
            assertAccepts(pom("pom.xml", "ch.ubique.internal", parent, "1"));
        }
    }

    @Test
    void acceptsLocalReactorInheritance() {
        assertAccepts(
                pom("pom.xml", "ch.uetlib", "uetlib-spring-boot-starter-parent", "1"),
                pom("module/pom.xml", "example", "application", "1"));
    }

    @Test
    void rejectsMissingUnrelatedAndSpoofedParentsWithoutMigratingOtherFiles() {
        for (final var parent :
                List.of(
                        "",
                        "<parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>4.1.2</version></parent>",
                        "<parent><groupId>example</groupId><artifactId>uetlib-parent</artifactId><version>1</version></parent>")) {
            final var errors = new ArrayList<Throwable>();
            final var context = new InMemoryExecutionContext(errors::add);
            final var sources = new ArrayList<SourceFile>();
            sources.add(
                    XmlParser.builder()
                            .build()
                            .parse("<project>" + parent + "</project>")
                            .findFirst()
                            .orElseThrow()
                            .withSourcePath(Path.of("pom.xml")));
            sources.add(
                    PlainText.builder()
                            .sourcePath(Path.of("application.properties"))
                            .text("ch.ubique.backend.job.interface.secret=test")
                            .build());
            final var recipe =
                    Environment.builder()
                            .scanRuntimeClasspath("ch.uetlib")
                            .build()
                            .activateRecipes("ch.uetlib.MigrateToPublicUetlib");
            final var run = recipe.run(new InMemoryLargeSourceSet(sources), context);
            assertFalse(errors.isEmpty());
            assertTrue(errors.getFirst().getMessage().contains("MigrateToPublicUetlib requires"));
            assertOnlyDiagnosticMarkers(run);
        }
    }

    @Test
    void rejectsMixedReactorEvenWhenAnotherProjectHasTheRightParent() {
        final var errors = new ArrayList<Throwable>();
        final var context = new InMemoryExecutionContext(errors::add);
        final var invalid =
                XmlParser.builder()
                        .build()
                        .parse("<project/>")
                        .findFirst()
                        .orElseThrow()
                        .withSourcePath(Path.of("other/pom.xml"));
        final var run =
                new RequireUetlibParent()
                        .run(
                                new InMemoryLargeSourceSet(
                                        List.of(
                                                pom("pom.xml", "ch.uetlib", "uetlib-parent", "1"),
                                                invalid)),
                                context);
        assertFalse(errors.isEmpty());
        assertOnlyDiagnosticMarkers(run);
    }

    private static void assertOnlyDiagnosticMarkers(final RecipeRun run) {
        for (final var result : run.getChangeset().getAllResults()) {
            assertEquals(
                    result.getBefore().printAll(),
                    ((SourceFile) result.getAfter().withMarkers(Markers.EMPTY)).printAll(),
                    "No partial source or resource edits");
        }
    }

    private static void assertAccepts(final SourceFile... sources) {
        final var errors = new ArrayList<Throwable>();
        new RequireUetlibParent()
                .run(
                        new InMemoryLargeSourceSet(List.of(sources)),
                        new InMemoryExecutionContext(errors::add));
        assertTrue(errors.isEmpty(), errors::toString);
    }

    private static SourceFile pom(
            final String path, final String group, final String artifact, final String version) {
        return XmlParser.builder()
                .build()
                .parse(
                        "<project><parent><groupId>"
                                + group
                                + "</groupId><artifactId>"
                                + artifact
                                + "</artifactId><version>"
                                + version
                                + "</version></parent><groupId>example</groupId><artifactId>application</artifactId><version>1</version></project>")
                .findFirst()
                .orElseThrow()
                .withSourcePath(Path.of(path));
    }
}
