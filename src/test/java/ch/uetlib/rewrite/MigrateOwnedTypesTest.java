package ch.uetlib.rewrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.text;

import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

@SuppressWarnings("java:S2699")
class MigrateOwnedTypesTest implements RewriteTest {
    @Override
    public void defaults(final RecipeSpec spec) {
        // These fixtures test transformations without Maven projects; the combined
        // recipe prerequisite is covered by RequireUetlibParentTest.
        spec.recipeFromResources("ch.uetlib.MigratePackages");
        spec.recipes(new MigrateConfigurationProperties(), spec.getRecipe());
    }

    @TestFactory
    Stream<DynamicTest> movesEveryKnownTypeFromOriginalAndIntermediateNamespaces() {
        return MigrateOwnedTypes.TYPES.entrySet().stream()
                .filter(entry -> !entry.getKey().contains("$"))
                .flatMap(
                        entry ->
                                Stream.of(
                                                entry.getKey(),
                                                entry.getKey()
                                                        .replace("ch.uetlib", "ch.ubique.backend"))
                                        .map(
                                                old ->
                                                        DynamicTest.dynamicTest(
                                                                old,
                                                                () ->
                                                                        assertTypeMoves(
                                                                                old,
                                                                                entry
                                                                                        .getValue()))));
    }

    private void assertTypeMoves(final String old, final String target) {
        final var name = old.substring(old.lastIndexOf('.') + 1);
        final var oldPackage = old.substring(0, old.lastIndexOf('.'));
        final var newPackage = target.substring(0, target.lastIndexOf('.'));
        final var consumer =
                """
                package example;

                import %s;

                class Consumer { %s value; }
                """;
        rewriteRun(
                spec -> spec.cycles(3).expectedCyclesThatMakeChanges(1),
                java(
                        "package " + oldPackage + "; public class " + name + " {}",
                        "package " + newPackage + "; public class " + name + " {}",
                        source ->
                                source.path("src/main/java/" + old.replace('.', '/') + ".java")
                                        .afterRecipe(
                                                result ->
                                                        assertEquals(
                                                                "src/main/java/"
                                                                        + target.replace('.', '/')
                                                                        + ".java",
                                                                result.getSourcePath()
                                                                        .toString()))),
                java(consumer.formatted(old, name), consumer.formatted(target, name)));
    }

    @Test
    void preservesUnrelatedTypesInSharedNamespaces() {
        rewriteRun(
                java("package ch.uetlib.core.jackson; public class OtherMapper {}"),
                java("package ch.uetlib.test; public class OtherTestHelper {}"),
                java("package ch.uetlib.image.webp; public class WebpCodec {}"),
                java("package ch.uetlib.spring.boot.http; public class OtherHttpHelper {}"));
    }

    @Test
    void movesNestedTypesReflectionStringsAndKnownProperties() {
        rewriteRun(
                java(
                        """
                        package ch.uetlib.image.webp;
                        public class Frame { public static class BufferedImageFrame {} }
                        """,
                        """
                        package ch.uetlib.core.image.webp;
                        public class Frame { public static class BufferedImageFrame {} }
                        """),
                java(
                        """
                        package example;
                        import ch.uetlib.image.webp.Frame.BufferedImageFrame;
                        class Config {
                            BufferedImageFrame frame;
                            String type = "ch.ubique.backend.image.webp.Frame$BufferedImageFrame";
                            String property = "${ch.ubique.backend.spring.boot.http.enabled}";
                        }
                        """,
                        """
                        package example;

                        import ch.uetlib.core.image.webp.Frame;

                        class Config {
                            Frame.BufferedImageFrame frame;
                            String type = "ch.uetlib.core.image.webp.Frame$BufferedImageFrame";
                            String property = "${ch.uetlib.spring.boot.http.enabled}";
                        }
                        """));
    }

    @Test
    void movesUnambiguousScanningRootsButKeepsPropertyPrefixesAndSharedRoots() {
        rewriteRun(
                java(
                        """
                        package example;
                        @interface ComponentScan { String[] value(); }
                        @interface ConfigurationProperties { String prefix(); }
                        @ComponentScan({"ch.ubique.backend.security", "ch.uetlib.cloud", "ch.uetlib.core"})
                        @ConfigurationProperties(prefix = "ch.ubique.backend.security")
                        class Config {}
                        """,
                        """
                        package example;
                        @interface ComponentScan { String[] value(); }
                        @interface ConfigurationProperties { String prefix(); }
                        @ComponentScan({"ch.uetlib.spring.boot.security", "ch.uetlib.spring.cloud", "ch.uetlib.core"})
                        @ConfigurationProperties(prefix = "ch.ubique.backend.security")
                        class Config {}
                        """));
    }

    @Test
    void movesLoggingAndProtobufPackagesWithoutChangingOtherResourceKeys() {
        rewriteRun(
                text(
                        "<configuration><logger name=\"ch.ubique.backend.security\"/><property value=\"${ch.ubique.backend.security.enabled}\"/></configuration>",
                        "<configuration><logger name=\"ch.uetlib.spring.boot.security\"/><property value=\"${ch.ubique.backend.security.enabled}\"/></configuration>",
                        spec -> spec.path("src/main/resources/logback-spring.xml")),
                text(
                        "package wire;\noption java_package = \"ch.uetlib.cloud.feign\";\n",
                        "package wire;\noption java_package = \"ch.uetlib.spring.cloud.feign\";\n",
                        spec -> spec.path("src/main/proto/client.proto")));
    }

    @Test
    void migratesFinalPackageNamesAndPreservesAlreadyMigratedStoragePackages() {
        rewriteRun(
                spec -> spec.cycles(3).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        class Config {
                            String databaseType = "ch.ubique.backend.geodb.entity.LV95CoordinateEmbeddable";
                            String jobType = "ch.uetlib.geojob.BaseTileJob";
                            String webpType = "ch.uetlib.image.webp.Webp$Still";
                            String coreImageType = "ch.uetlib.image.webp.Frame$PathFrame";
                            String storageType = "ch.uetlib.storage.spring.proxy.StorageProxyAutoConfiguration";
                            String alreadyMigrated = "ch.uetlib.storage.spring.boot.proxy.StorageProxyAutoConfiguration";
                            String property = "${ch.ubique.backend.storage.spring.enabled}";
                        }
                        """,
                        """
                        class Config {
                            String databaseType = "ch.uetlib.geo.db.entity.LV95CoordinateEmbeddable";
                            String jobType = "ch.uetlib.geo.job.BaseTileJob";
                            String webpType = "ch.uetlib.webp.Webp$Still";
                            String coreImageType = "ch.uetlib.core.image.webp.Frame$PathFrame";
                            String storageType = "ch.uetlib.storage.spring.boot.proxy.StorageProxyAutoConfiguration";
                            String alreadyMigrated = "ch.uetlib.storage.spring.boot.proxy.StorageProxyAutoConfiguration";
                            String property = "${ch.uetlib.storage.spring.enabled}";
                        }
                        """),
                text(
                        "ch.ubique.backend.storage.spring.proxy.StorageProxyAutoConfiguration\n",
                        "ch.uetlib.storage.spring.boot.proxy.StorageProxyAutoConfiguration\n",
                        spec ->
                                spec.path(
                                        "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")));
    }

    @Test
    void migratesResourceContentAndServiceFilenameFromBothNamespaces() {
        rewriteRun(
                text(
                        "ch.ubique.backend.spring.validation.UetlibSpringBootValidationResponseValidationAutoConfiguration\n",
                        "ch.uetlib.spring.boot.validation.UetlibSpringBootValidationResponseValidationAutoConfiguration\n",
                        spec ->
                                spec.path(
                                        "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")),
                text(
                        "ch.uetlib.core.jackson.JacksonStringToEnumConverterFactory\n",
                        "ch.uetlib.spring.boot.jackson.JacksonStringToEnumConverterFactory\n",
                        spec ->
                                spec.path(
                                                "src/main/resources/META-INF/services/ch.uetlib.core.jackson.JacksonEnumToStringConverterFactory")
                                        .afterRecipe(
                                                source ->
                                                        assertEquals(
                                                                "src/main/resources/META-INF/services/ch.uetlib.spring.boot.jackson.JacksonEnumToStringConverterFactory",
                                                                source.getSourcePath()
                                                                        .toString()))),
                text(
                        "type=ch.uetlib.security.JwtUtil\nch.ubique.backend.security.enabled=true\n",
                        "type=ch.uetlib.spring.boot.security.JwtUtil\nch.ubique.backend.security.enabled=true\n",
                        spec -> spec.path("src/main/resources/application.properties")));
    }
}
