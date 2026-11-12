package ch.uetlib.rewrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.yaml.Assertions.yaml;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.xml.Assertions;

@SuppressWarnings("java:S2699")
class MigrateConfigurationPropertiesTest implements RewriteTest {
    @Test
    void migratesQueuePrereleaseConfigurationWithoutChangingValues() {
        rewriteRun(
                properties(
                        """
                        ch.ubique.backend.job.check.cancelled-queued-runs=WARN
                        ch.ubique.backend.job.check.single-primary-instance=ERROR
                        ch.ubique.backend.job.check.single-primary-instance.startup-grace=PT2M
                        ch.ubique.backend.job.instance.check-in=PT30S
                        ch.ubique.backend.job.instance.purge-after=P7D
                        ch.ubique.backend.job.instance.stale-after=PT2M
                        ch.ubique.backend.job.runner.check-for-runnable.cron=-
                        ch.ubique.backend.job.runner.delete-old-cancelled-queued.cron=-
                        ch.ubique.backend.job.runner.expire-unclaimable-queued.cron=-
                        ch.ubique.backend.job.runner.instance.name=queue-worker
                        ch.ubique.backend.job.runner.instance.taints=test
                        ch.ubique.backend.job.runner.max.pages.per.check=3
                        ch.ubique.backend.job.runner.max.runnable.per.check=5
                        ch.ubique.backend.job.runner.reconcile-running.cron=-
                        ch.ubique.backend.job.runner.reset-abandoned-claims.cron=-
                        ch.ubique.backend.job.runner.unknown-job.timeout=PT5M
                        """,
                        """
                        ch.uetlib.job.check.cancelled-queued-runs=WARN
                        ch.uetlib.job.check.single-primary-instance=ERROR
                        ch.uetlib.job.check.single-primary-instance.startup-grace=PT2M
                        ch.uetlib.job.instance.check-in=PT30S
                        ch.uetlib.job.instance.purge-after=P7D
                        ch.uetlib.job.instance.stale-after=PT2M
                        ch.uetlib.job.runner.check-for-runnable.cron=-
                        ch.uetlib.job.runner.delete-old-cancelled-queued.cron=-
                        ch.uetlib.job.runner.expire-unclaimable-queued.cron=-
                        ch.uetlib.job.runner.instance.name=queue-worker
                        ch.uetlib.job.runner.instance.taints=test
                        ch.uetlib.job.runner.max.pages.per.check=3
                        ch.uetlib.job.runner.max.runnable.per.check=5
                        ch.uetlib.job.runner.reconcile-running.cron=-
                        ch.uetlib.job.runner.reset-abandoned-claims.cron=-
                        ch.uetlib.job.runner.unknown-job.timeout=PT5M
                        """,
                        spec -> spec.path("src/main/resources/application.properties")));
    }

    @Test
    void migratesQueuePrereleasePlaceholdersAndDeploymentEnvironment() {
        rewriteRun(
                java(
                        """
                        class QueueConfig {
                            @interface Value { String value(); }
                            @Value("${ch.ubique.backend.job.check.single-primary-instance:ERROR}")
                            String level;
                            String name = System.getProperty("ch.ubique.backend.job.runner.instance.name");
                        }
                        """,
                        """
                        class QueueConfig {
                            @interface Value { String value(); }
                            @Value("${ch.uetlib.job.check.single-primary-instance:ERROR}")
                            String level;
                            String name = System.getProperty("ch.uetlib.job.runner.instance.name");
                        }
                        """),
                yaml(
                        "ch.ubique.uetlib.job.check.single-primary-instance: WARN\n",
                        "ch.uetlib.job.check.single-primary-instance: WARN\n",
                        spec -> spec.path("src/main/resources/application.yml")),
                text(
                        "env:\n  - name: CH_UBIQUE_BACKEND_JOB_RUNNER_INSTANCE_NAME\n    value: worker\n",
                        "env:\n  - name: CH_UETLIB_JOB_RUNNER_INSTANCE_NAME\n    value: worker\n",
                        spec -> spec.path("deploy/deployment.yaml")));
    }

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
                Assertions.xml(
                        "<project><parent><groupId>ch.ubique.internal</groupId><artifactId>ubique-spring-boot-starter-parent</artifactId><version>4.1.2-2026.11.2</version></parent></project>",
                        spec -> spec.path("pom.xml")),
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
    public void defaults(final RecipeSpec spec) {
        spec.recipe(new MigrateConfigurationProperties());
    }

    @Test
    void migratesEveryKnownPrefixAndPreservesUnknownNamespaces() {
        for (final var entry : MigrateConfigurationProperties.PREFIXES) {
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
                Assertions.xml(
                        "<project><parent><groupId>ch.ubique.internal</groupId><artifactId>ubique-spring-boot-starter-parent</artifactId><version>4.1.2-2026.11.2</version></parent></project>",
                        spec -> spec.path("pom.xml")),
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
