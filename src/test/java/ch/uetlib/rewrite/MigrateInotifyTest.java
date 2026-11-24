package ch.uetlib.rewrite;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

@SuppressWarnings("java:S2699")
class MigrateInotifyTest implements RewriteTest {

    @Override
    public void defaults(final RecipeSpec spec) {
        spec.recipeFromResources("ch.uetlib.MigrateToPublicUetlib");
    }

    @Test
    void migratesOriginalCoordinatesDeclarationsImportsAndSourcePaths() {
        rewriteRun(
                xml(
                        "<project><parent><groupId>ch.ubique.internal</groupId><artifactId>ubique-spring-boot-starter-parent</artifactId><version>4.1.2-2026.11.2</version></parent><dependencies><dependency><groupId>ch.ubique</groupId><artifactId>inotify</artifactId><version>2.0</version></dependency></dependencies></project>",
                        "<project><parent><groupId>ch.ubique.internal</groupId><artifactId>ubique-spring-boot-starter-parent</artifactId><version>4.1.2-2026.11.2</version></parent><dependencies><dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-inotify-java</artifactId><version>3.1.0</version></dependency></dependencies></project>",
                        spec -> spec.path("pom.xml")),
                java(
                        "package com.den_4.inotify_java; public class NativeInotify {}",
                        "package ch.uetlib.inotify; public class NativeInotify {}",
                        spec ->
                                spec.path("src/main/java/com/den_4/inotify_java/NativeInotify.java")
                                        .afterRecipe(source ->
                                                org.junit.jupiter.api.Assertions.assertEquals(
                                                        "src/main/java/ch/uetlib/inotify/NativeInotify.java",
                                                        source.getSourcePath().toString()))),
                java(
                        """
                        package example;
                        import com.den_4.inotify_java.NativeInotify;
                        class Consumer {
                            NativeInotify value;
                            com.den_4.inotify_java.NativeInotify qualified;
                        }
                        """,
                        """
                        package example;
                        import ch.uetlib.inotify.NativeInotify;
                        class Consumer {
                            NativeInotify value;
                            ch.uetlib.inotify.NativeInotify qualified;
                        }
                        """));
    }

    @Test
    void migratesClassNameStringsAndScanningWhilePreservingPropertyNames() {
        rewriteRun(
                xml(
                        "<project><parent><groupId>ch.ubique.internal</groupId><artifactId>ubique-spring-boot-starter-parent</artifactId><version>4.1.2-2026.11.2</version></parent></project>",
                        spec -> spec.path("pom.xml")),
                java(
                        """
                        package example;
                        @interface ComponentScan { String value(); }
                        @ComponentScan("com.den_4.inotify_java")
                        class Config {
                            String type = "com.den_4.inotify_java.exceptions.InotifyException";
                            String key = "com.den_4.inotify_java.enabled";
                            String unrelated = "com.den_4.inotify_javaother.NativeInotify";
                        }
                        """,
                        """
                        package example;
                        @interface ComponentScan { String value(); }
                        @ComponentScan("ch.uetlib.inotify")
                        class Config {
                            String type = "ch.uetlib.inotify.exceptions.InotifyException";
                            String key = "com.den_4.inotify_java.enabled";
                            String unrelated = "com.den_4.inotify_javaother.NativeInotify";
                        }
                        """));
    }

    @Test
    void migratesPropertiesYamlAndServiceRegistrations() {
        rewriteRun(
                xml(
                        "<project><parent><groupId>ch.ubique.internal</groupId><artifactId>ubique-spring-boot-starter-parent</artifactId><version>4.1.2-2026.11.2</version></parent></project>",
                        spec -> spec.path("pom.xml")),
                text(
                        "com.den_4.inotify_java.enabled=true\nhandler=com.den_4.inotify_java.NativeInotify\n",
                        "com.den_4.inotify_java.enabled=true\nhandler=ch.uetlib.inotify.NativeInotify\n",
                        spec -> spec.path("src/main/resources/application.properties")),
                text(
                        "handler=com.den_4.inotify_java.exceptions.InotifyException\n",
                        "handler=ch.uetlib.inotify.exceptions.InotifyException\n",
                        spec -> spec.path("src/main/resources/custom.properties")),
                text(
                        "handler: com.den_4.inotify_java.NativeInotify\n",
                        "handler: ch.uetlib.inotify.NativeInotify\n",
                        spec -> spec.path("src/main/resources/application.yml")),
                text(
                        "com.den_4.inotify_java.NativeInotify\n",
                        "ch.uetlib.inotify.NativeInotify\n",
                        spec ->
                                spec.path(
                                                "src/main/resources/META-INF/services/com.den_4.inotify_java.Inotify")
                                        .afterRecipe(source ->
                                                org.junit.jupiter.api.Assertions.assertEquals(
                                                        "src/main/resources/META-INF/services/ch.uetlib.inotify.Inotify",
                                                        source.getSourcePath().toString()))));
    }

    @Test
    void migratesManagedAndVersionlessDependenciesAndPreservesIntermediateVersions() {
        rewriteRun(
                xml(
                        """
                        <project><parent><groupId>ch.ubique.internal</groupId><artifactId>ubique-spring-boot-starter-parent</artifactId><version>4.1.2-2026.11.2</version></parent>
                            <dependencyManagement><dependencies><dependency>
                                <groupId>ch.ubique</groupId><artifactId>inotify</artifactId><version>${inotify.version}</version>
                            </dependency></dependencies></dependencyManagement>
                            <dependencies>
                                <dependency><groupId>ch.ubique</groupId><artifactId>inotify</artifactId><scope>test</scope></dependency>
                                <dependency><groupId>ch.ubique.uetlib</groupId><artifactId>uetlib-inotify-java</artifactId><version>3.2.0</version></dependency>
                                <dependency><groupId>ch.ubique</groupId><artifactId>uetlib-inotify-java</artifactId><version>2.0</version></dependency>
                            </dependencies>
                        </project>
                        """,
                        """
                        <project><parent><groupId>ch.ubique.internal</groupId><artifactId>ubique-spring-boot-starter-parent</artifactId><version>4.1.2-2026.11.2</version></parent>
                            <dependencyManagement><dependencies><dependency>
                                <groupId>ch.uetlib</groupId><artifactId>uetlib-inotify-java</artifactId><version>3.1.0</version>
                            </dependency></dependencies></dependencyManagement>
                            <dependencies>
                                <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-inotify-java</artifactId><scope>test</scope></dependency>
                                <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-inotify-java</artifactId><version>3.2.0</version></dependency>
                                <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-inotify-java</artifactId><version>3.1.0</version></dependency>
                            </dependencies>
                        </project>
                        """,
                        spec -> spec.path("pom.xml")));
    }

    @Test
    void leavesUnrelatedAndAlreadyMigratedCoordinatesUnchanged() {
        rewriteRun(
                xml(
                        """
                        <project><parent><groupId>ch.ubique.internal</groupId><artifactId>ubique-spring-boot-starter-parent</artifactId><version>4.1.2-2026.11.2</version></parent><dependencies>
                            <dependency><groupId>other</groupId><artifactId>inotify</artifactId><version>2.0</version></dependency>
                            <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-inotify-java</artifactId><version>3.1.0</version></dependency>
                        </dependencies></project>
                        """,
                        spec -> spec.path("pom.xml")),
                java("package com.den_4.inotify_javaother; class Unrelated {}"));
    }
}
