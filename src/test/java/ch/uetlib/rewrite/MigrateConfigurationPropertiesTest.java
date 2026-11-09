package ch.uetlib.rewrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.yaml.Assertions.yaml;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class MigrateConfigurationPropertiesTest implements RewriteTest {
    @Test
    void doesNotRewriteMavenGroupIdsThatLookLikePropertyPrefixes() {
        rewriteRun(
                text(
                        "<project><groupId>ch.ubique.backend.job</groupId><artifactId>example</artifactId><version>1</version></project>",
                        spec -> spec.path("pom.xml")));
    }

    @Test
    void keepsPackageScansSeparateFromPropertyBindings() {
        rewriteRun(
                spec -> spec.recipeFromResources("ch.uetlib.MigrateToPublicUetlib"),
                java(
                        """
                        package example;
                        @interface ComponentScan { String value(); }
                        @interface ConfigurationProperties { String prefix(); }
                        @ComponentScan("ch.ubique.backend.job.testapp")
                        @ConfigurationProperties(prefix = "ch.ubique.backend.job.testapp")
                        class Config {}
                        """,
                        """
                        package example;
                        @interface ComponentScan { String value(); }
                        @interface ConfigurationProperties { String prefix(); }
                        @ComponentScan("ch.uetlib.job.testapp")
                        @ConfigurationProperties(prefix = "ch.uetlib.testapp")
                        class Config {}
                        """));
    }

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateConfigurationProperties());
    }

    @Test
    void migratesEveryKnownPrefixAndPreservesUnknownNamespaces() {
        for (var entry : MigrateConfigurationProperties.PREFIXES) {
            assertEquals(
                    entry.getValue() + ".some-value",
                    MigrateConfigurationProperties.migrateText(entry.getKey() + ".some-value"));
        }
        assertEquals(
                "other.ch.ubique.backend.job.enabled",
                MigrateConfigurationProperties.migrateText("other.ch.ubique.backend.job.enabled"));
        assertEquals(
                "ch.ubique.backend.unrelated.enabled",
                MigrateConfigurationProperties.migrateText("ch.ubique.backend.unrelated.enabled"));
        assertEquals(
                "ch.ubique.backend.job.Example",
                MigrateConfigurationProperties.migrateText("ch.ubique.backend.job.Example"));
    }

    @Test
    void migratesBindingsConditionsValuesSchedulingLookupsAndTestProperties() {
        rewriteRun(
                java(
                        """
                package example;
                @interface ConfigurationProperties { String prefix(); }
                @interface ConditionalOnProperty { String prefix(); String[] name(); }
                @interface Value { String value(); }
                @interface Scheduled { String cron(); }
                @interface SpringBootTest { String[] properties(); }
                @ConfigurationProperties(prefix = "ch.ubique.backend.spring.boot.http")
                @ConditionalOnProperty(prefix = "ch.ubique.backend.storage.proxy", name = "enabled")
                @SpringBootTest(properties = "ch.ubique.backend.spring.boot.problem.include-constraint-violations=true")
                class Config {
                    static final String PREFIX = "ch.ubique.backend.payment.postfinance";
                    @Value("${ch.ubique.backend.job.cron.timezone:${ch.ubique.backend.job.default-zone:UTC}}")
                    String timezone;
                    @Value("${ch.ubique.datatrans.merchant-id}") String merchant;
                    @Scheduled(cron = "${ch.ubique.backend.job.job-timeout-watcher.cron:30 */5 * * * ?}")
                    void tick() {}
                    String lookup() { return System.getProperty("ch.ubique.backend.monitoring.check-endpoint"); }
                }
                """,
                        """
                package example;
                @interface ConfigurationProperties { String prefix(); }
                @interface ConditionalOnProperty { String prefix(); String[] name(); }
                @interface Value { String value(); }
                @interface Scheduled { String cron(); }
                @interface SpringBootTest { String[] properties(); }
                @ConfigurationProperties(prefix = "ch.uetlib.spring.boot.http")
                @ConditionalOnProperty(prefix = "ch.uetlib.storage.proxy", name = "enabled")
                @SpringBootTest(properties = "ch.uetlib.spring.boot.problem.include-constraint-violations=true")
                class Config {
                    static final String PREFIX = "ch.uetlib.payment.postfinance";
                    @Value("${ch.uetlib.job.cron.timezone:${ch.uetlib.job.default-zone:UTC}}")
                    String timezone;
                    @Value("${ch.uetlib.payment.datatrans.client.merchant-id}") String merchant;
                    @Scheduled(cron = "${ch.uetlib.job.job-timeout-watcher.cron:30 */5 * * * ?}")
                    void tick() {}
                    String lookup() { return System.getProperty("ch.uetlib.monitoring.check-endpoint"); }
                }
                """));
    }

    @Test
    void migratesPropertyKeysAndPlaceholderValuesIncludingDatatransBridge() {
        rewriteRun(
                properties(
                        """
                # Keep formatting and values.
                ch.ubique.backend.spring.boot.http.default-user-agent=example
                ch.ubique.backend.spring.validation.response.fail.enabled=true
                ch.ubique.backend.job.testapp.logbook.body-filter.enabled=false
                ch.ubique.datatrans.merchant-id=${ch.ubique.backend.payment.datatrans.merchant-id}
                ch.ubique.datatrans.password=${CH_UBIQUE_BACKEND_PAYMENT_DATATRANS_PASSWORD:}
                other.value=${ch.ubique.backend.job.cron.timezone:UTC}
                ch.ubique.backend.unrelated.enabled=true
                """,
                        """
                # Keep formatting and values.
                ch.uetlib.spring.boot.http.default-user-agent=example
                ch.uetlib.spring.boot.validation.response.fail.enabled=true
                ch.uetlib.testapp.logbook.body-filter.enabled=false
                ch.uetlib.payment.datatrans.client.merchant-id=${ch.uetlib.payment.datatrans.merchant-id}
                ch.uetlib.payment.datatrans.client.password=${CH_UETLIB_PAYMENT_DATATRANS_PASSWORD:}
                other.value=${ch.uetlib.job.cron.timezone:UTC}
                ch.ubique.backend.unrelated.enabled=true
                """,
                        spec -> spec.path("src/main/resources/custom.properties")));
    }

    @Test
    void migratesNestedAndFlatYamlAndPreservesUnrelatedSiblings() {
        rewriteRun(
                yaml(
                        """
                ch:
                  ubique:
                    backend:
                      job:
                        cron:
                          timezone: UTC
                      unrelated:
                        enabled: true
                ch.ubique.backend.storage.proxy.enabled: true
                fallback: ${ch.ubique.backend.job.cron.timezone:UTC}
                """,
                        """
                ch:
                  ubique:
                    backend:
                      unrelated:
                        enabled: true
                  uetlib.job:
                    cron:
                      timezone: UTC
                ch.uetlib.storage.proxy.enabled: true
                fallback: ${ch.uetlib.job.cron.timezone:UTC}
                """,
                        spec -> spec.path("src/main/resources/application.yml")));
    }

    @Test
    void migratesDeploymentEnvironmentVariablesXmlAndMetadata() {
        rewriteRun(
                text(
                        "env:\n  - name: CH_UBIQUE_BACKEND_JOB_INTERFACE_SECRET\n    value: ${ch.ubique.backend.job.interface.secret}\n",
                        "env:\n  - name: CH_UETLIB_JOB_INTERFACE_SECRET\n    value: ${ch.uetlib.job.interface.secret}\n",
                        spec -> spec.path("deploy/deployment.yaml")),
                text(
                        "<bean value=\"${ch.ubique.backend.monitoring.cors-origins:}\"/>",
                        "<bean value=\"${ch.uetlib.monitoring.cors-origins:}\"/>",
                        spec -> spec.path("src/main/resources/context.xml")),
                text(
                        "{\"properties\":[{\"name\":\"ch.ubique.backend.spring.boot.http.default-user-agent\",\"type\":\"java.lang.String\"}]}",
                        "{\"properties\":[{\"name\":\"ch.uetlib.spring.boot.http.default-user-agent\",\"type\":\"java.lang.String\"}]}",
                        spec ->
                                spec.path(
                                        "src/main/resources/META-INF/additional-spring-configuration-metadata.json")));
    }

    @Test
    void combinedRecipeMigratesPropertiesAndClassesIndependently() {
        rewriteRun(
                spec -> spec.recipeFromResources("ch.uetlib.MigrateToPublicUetlib"),
                java(
                        """
                        package example;
                        class Config {
                            String key = "ch.ubique.backend.job.enabled";
                            String type = "ch.ubique.backend.job.SomeJob";
                        }
                        """,
                        """
                        package example;
                        class Config {
                            String key = "ch.uetlib.job.enabled";
                            String type = "ch.uetlib.job.SomeJob";
                        }
                        """));
    }

    @Test
    void leavesAlreadyMigratedPropertiesUntouched() {
        rewriteRun(
                properties("ch.uetlib.job.enabled=true\n"),
                yaml("ch.uetlib.storage.proxy.enabled: true\n"),
                java("package example; class Config { String key = \"ch.uetlib.job.enabled\"; }"));
    }
}
