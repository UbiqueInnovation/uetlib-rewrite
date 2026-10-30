package ch.ubique.uetlib.rewrite;

import static org.openrewrite.xml.Assertions.xml;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class MigrateMavenCoordinatesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateMavenCoordinates());
    }

    @Test
    void migratesParentsManagedDependenciesAndVersionExpressions() {
        rewriteRun(
                xml(
                        """
                <project>
                    <parent><groupId>ch.ubique</groupId><artifactId>uetlib-parent</artifactId><version>2026.10.1</version></parent>
                    <artifactId>private-application</artifactId>
                    <dependencyManagement><dependencies>
                        <dependency><groupId>ch.ubique</groupId><artifactId>uetlib-self-bom</artifactId><version>${uetlib.version}</version><type>pom</type><scope>import</scope></dependency>
                    </dependencies></dependencyManagement>
                    <dependencies>
                        <dependency><groupId>ch.ubique</groupId><artifactId>uetlib-core</artifactId><version>2.7.0</version></dependency>
                        <dependency><groupId>ch.ubique</groupId><artifactId>uetlib-storage</artifactId></dependency>
                        <dependency><groupId>ch.ubique</groupId><artifactId>inotify</artifactId><version>2.0</version></dependency>
                    </dependencies>
                </project>
                """,
                        """
                <project>
                    <parent><groupId>ch.ubique.uetlib</groupId><artifactId>uetlib-parent</artifactId><version>2026.11.0</version></parent>
                    <artifactId>private-application</artifactId>
                    <dependencyManagement><dependencies>
                        <dependency><groupId>ch.ubique.uetlib</groupId><artifactId>uetlib-self-bom</artifactId><version>6.0.0</version><type>pom</type><scope>import</scope></dependency>
                    </dependencies></dependencyManagement>
                    <dependencies>
                        <dependency><groupId>ch.ubique.uetlib</groupId><artifactId>uetlib-core</artifactId><version>3.0.0</version></dependency>
                        <dependency><groupId>ch.ubique.uetlib</groupId><artifactId>uetlib-storage</artifactId></dependency>
                        <dependency><groupId>ch.ubique</groupId><artifactId>inotify</artifactId><version>2.0</version></dependency>
                    </dependencies>
                </project>
                """,
                        spec -> spec.path("pom.xml")));
    }

    @Test
    void leavesAlreadyMigratedCoordinatesUnchanged() {
        rewriteRun(
                xml(
                        "<project><groupId>ch.ubique.uetlib</groupId><artifactId>uetlib-core</artifactId><version>3.0.0</version></project>",
                        spec -> spec.path("pom.xml")));
    }

    @Test
    void doesNotChangeArbitraryXmlConfiguration() {
        rewriteRun(
                xml(
                        "<configuration><groupId>ch.ubique</groupId><artifactId>uetlib-core</artifactId><version>2.7.0</version></configuration>",
                        spec -> spec.path("config.xml")));
    }

    @Test
    void combinesLegacyDatatransDependenciesWithoutChangingItsParent() {
        rewriteRun(
                xml(
                        """
            <project>
                <parent><groupId>ch.ubique.datatrans</groupId><artifactId>datatrans-api-client</artifactId><version>1.0.0</version></parent>
                <dependencies>
                    <dependency><groupId>ch.ubique.datatrans</groupId><artifactId>datatrans-api-client-model</artifactId><version>1.0.0</version></dependency>
                    <dependency><groupId>ch.ubique.datatrans</groupId><artifactId>datatrans-api-client-service</artifactId><version>1.0.0</version></dependency>
                </dependencies>
            </project>
            """,
                        """
            <project>
                <parent><groupId>ch.ubique.datatrans</groupId><artifactId>datatrans-api-client</artifactId><version>1.0.0</version></parent>
                <dependencies>
                    <dependency><groupId>ch.ubique.uetlib</groupId><artifactId>uetlib-payment-datatrans</artifactId><version>3.0.0</version></dependency>
                </dependencies>
            </project>
            """,
                        spec -> spec.path("pom.xml")));
    }
}
