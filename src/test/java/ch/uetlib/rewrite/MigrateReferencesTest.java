package ch.uetlib.rewrite;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.text;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class MigrateReferencesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("ch.uetlib.MigratePackages");
    }

    @Test
    void migratesIntermediateClassNamesInPropertiesAndPreservesKeys() {
        rewriteRun(
                text(
                        "ch.ubique.uetlib.job.enabled=true\nhandler=ch.ubique.uetlib.job.TestJob\n",
                        "ch.ubique.uetlib.job.enabled=true\nhandler=ch.uetlib.job.TestJob\n",
                        spec -> spec.path("src/main/resources/application.properties")),
                text(
                        "handler=ch.ubique.uetlib.core.Example\n",
                        "handler=ch.uetlib.core.Example\n",
                        spec -> spec.path("src/main/resources/custom.properties")));
    }

    @Test
    void changesClassNameStringsButPreservesPropertyKeys() {
        rewriteRun(
                java(
                        """
                package example;
                class Config {
                    String property = "ch.ubique.backend.job.enabled";
                    String type = "ch.ubique.backend.job.TestJob";
                }
                """,
                        """
                package example;
                class Config {
                    String property = "ch.ubique.backend.job.enabled";
                    String type = "ch.uetlib.job.TestJob";
                }
                """));
    }

    @Test
    void changesScanPackagesButPreservesConfigurationProperties() {
        rewriteRun(
                java(
                        """
                package ch.ubique.backend.example;
                @interface ComponentScan { String value(); }
                @interface ConfigurationProperties { String prefix(); }
                @ComponentScan("ch.ubique.backend.job")
                @ConfigurationProperties(prefix = "ch.ubique.backend.job")
                class Config {}
                """,
                        """
                package ch.uetlib.example;
                @interface ComponentScan { String value(); }
                @interface ConfigurationProperties { String prefix(); }
                @ComponentScan("ch.uetlib.job")
                @ConfigurationProperties(prefix = "ch.ubique.backend.job")
                class Config {}
                """));
    }

    @Test
    void changesServiceRegistrationContentAndFileName() {
        rewriteRun(
                text(
                        "ch.ubique.backend.core.Provider\n",
                        "ch.uetlib.core.Provider\n",
                        spec ->
                                spec.path(
                                                "src/main/resources/META-INF/services/ch.ubique.backend.core.Service")
                                        .afterRecipe(
                                                source ->
                                                        org.junit.jupiter.api.Assertions
                                                                .assertEquals(
                                                                        "src/main/resources/META-INF/services/ch.uetlib.core.Service",
                                                                        source.getSourcePath()
                                                                                .toString()))));
    }

    @Test
    void changesSpringImportsAndResourceClassNames() {
        rewriteRun(
                text(
                        "ch.ubique.backend.spring.Config\n",
                        "ch.uetlib.spring.Config\n",
                        spec ->
                                spec.path(
                                        "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")),
                text(
                        "handler=ch.ubique.backend.job.TestJob\nch.ubique.backend.job.enabled=true\n",
                        "handler=ch.uetlib.job.TestJob\nch.ubique.backend.job.enabled=true\n",
                        spec -> spec.path("src/main/resources/application.properties")));
    }

    @Test
    void changesOnlyJavaPackageInProtobufSchemas() {
        rewriteRun(
                text(
                        "package ch.ubique.backend.wire;\noption java_package = \"ch.ubique.backend.geo.protobuf\";\n",
                        "package ch.ubique.backend.wire;\noption java_package = \"ch.uetlib.geo.protobuf\";\n",
                        spec -> spec.path("src/main/proto/example.proto")));
    }

    @Test
    void preservesPropertyPlaceholdersInLogback() {
        rewriteRun(
                text(
                        "<configuration><logger name=\"ch.ubique.backend.job\"/><property value=\"${ch.ubique.backend.job.enabled}\"/></configuration>",
                        "<configuration><logger name=\"ch.uetlib.job\"/><property value=\"${ch.ubique.backend.job.enabled}\"/></configuration>",
                        spec -> spec.path("src/main/resources/logback-spring.xml")));
    }

    @Test
    void migratesDatatransTypesAndStringsButKeepsCredentialProperties() {
        rewriteRun(
                java(
                        """
            package ch.ubique.datatrans.model;
            class Transaction {
                String type = "ch.ubique.datatrans.model.Transaction";
                String property = "${ch.ubique.datatrans.merchant-id}";
            }
            """,
                        """
            package ch.uetlib.payment.datatrans.client.model;
            class Transaction {
                String type = "ch.uetlib.payment.datatrans.client.model.Transaction";
                String property = "${ch.ubique.datatrans.merchant-id}";
            }
            """),
                text(
                        "ch.ubique.datatrans.service.autoconfigure.DatatransApiClientAutoConfiguration\n",
                        "ch.uetlib.payment.datatrans.client.service.autoconfigure.DatatransApiClientAutoConfiguration\n",
                        spec ->
                                spec.path(
                                        "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")));
    }

    @Test
    void migratesNamedLoggerCategory() {
        rewriteRun(
                java(
                        """
            class Logging {
                static final String LOGGER_NAME = "ch.ubique.backend.payment.postfinance.http";
            }
            """,
                        """
            class Logging {
                static final String LOGGER_NAME = "ch.uetlib.payment.postfinance.http";
            }
            """));
    }
}
