package ch.uetlib.rewrite;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class MigrateGeoSpringBootTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("ch.uetlib.MigrateToPublicUetlib");
    }

    @TestFactory
    Stream<DynamicTest> migratesOriginalAndIntermediateCoordinatesAndPackages() {
        return Stream.of("ch.ubique", "ch.ubique.uetlib", "ch.uetlib")
                .map(
                        group ->
                                DynamicTest.dynamicTest(
                                        group,
                                        () -> {
                                            String oldPackage =
                                                    group.equals("ch.ubique")
                                                            ? "ch.ubique.backend.spring.boot.geo"
                                                            : group + ".spring.boot.geo";
                                            String version =
                                                    group.equals("ch.ubique") ? "4.0.0" : "5.1.0";
                                            rewriteRun(
                                                    xml(
                                                            "<project><dependencies><dependency><groupId>"
                                                                    + group
                                                                    + "</groupId><artifactId>uetlib-spring-boot-geo</artifactId><version>5.1.0</version></dependency></dependencies></project>",
                                                            "<project><dependencies><dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-geo-spring-boot</artifactId><version>"
                                                                    + version
                                                                    + "</version></dependency></dependencies></project>",
                                                            spec -> spec.path("pom.xml")),
                                                    java(
                                                            "package "
                                                                    + oldPackage
                                                                    + ".springdoc; public class Config {}",
                                                            "package ch.uetlib.geo.spring.boot.springdoc; public class Config {}"),
                                                    java(
                                                            "package example; import "
                                                                    + oldPackage
                                                                    + ".springdoc.Config; class Consumer { Config config; String type = \""
                                                                    + oldPackage
                                                                    + ".springdoc.Config\"; }",
                                                            "package example; import ch.uetlib.geo.spring.boot.springdoc.Config; class Consumer { Config config; String type = \"ch.uetlib.geo.spring.boot.springdoc.Config\"; }"),
                                                    text(
                                                            oldPackage + ".springdoc.Config\n",
                                                            "ch.uetlib.geo.spring.boot.springdoc.Config\n",
                                                            spec ->
                                                                    spec.path(
                                                                            "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")),
                                                    text(
                                                            "handler="
                                                                    + oldPackage
                                                                    + ".springdoc.Config\n",
                                                            "handler=ch.uetlib.geo.spring.boot.springdoc.Config\n",
                                                            spec ->
                                                                    spec.path(
                                                                            "src/main/resources/application.properties")));
                                        }));
    }

    @Test
    void handlesManagedVersionlessAndAlreadyMigratedDependencies() {
        rewriteRun(
                xml(
                        """
                <project>
                    <dependencyManagement><dependencies><dependency>
                        <groupId>ch.ubique</groupId><artifactId>uetlib-spring-boot-geo</artifactId><version>2.0.0</version>
                    </dependency></dependencies></dependencyManagement>
                    <dependencies><dependency><groupId>ch.ubique</groupId><artifactId>uetlib-spring-boot-geo</artifactId></dependency></dependencies>
                </project>
                """,
                        """
                <project>
                    <dependencyManagement><dependencies><dependency>
                        <groupId>ch.uetlib</groupId><artifactId>uetlib-geo-spring-boot</artifactId><version>4.0.0</version>
                    </dependency></dependencies></dependencyManagement>
                    <dependencies><dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-geo-spring-boot</artifactId></dependency></dependencies>
                </project>
                """,
                        spec -> spec.path("pom.xml")),
                xml(
                        "<project><dependencies><dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-geo-spring-boot</artifactId><version>4.0.0</version></dependency></dependencies></project>",
                        spec -> spec.path("ready/pom.xml")),
                java("package ch.uetlib.geo.spring.boot; class AlreadyMigrated {}"));
    }
}
