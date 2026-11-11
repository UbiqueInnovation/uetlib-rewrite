package ch.uetlib.rewrite;

import static org.openrewrite.xml.Assertions.xml;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

@SuppressWarnings("java:S2699")
class MigrateMavenCoordinatesTest implements RewriteTest {
    @Test
    void upgradesQueuePrereleasesAcrossOriginalAndPublicGroups() {
        for (final var group : java.util.List.of("ch.ubique", "ch.ubique.uetlib", "ch.uetlib")) {
            for (final var version : java.util.List.of("6.0.0-alpha.1", "6.0.0-alpha.16")) {
                rewriteRun(
                        xml(
                                "<project><dependencies><dependency><groupId>"
                                        + group
                                        + "</groupId><artifactId>uetlib-job</artifactId><version>"
                                        + version
                                        + "</version></dependency></dependencies></project>",
                                "<project><dependencies><dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-job</artifactId><version>6.0.3</version></dependency></dependencies></project>",
                                spec -> spec.path("pom.xml")));
            }
        }
        rewriteRun(
                xml(
                        "<project><dependencies><dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-job</artifactId><version>6.0.3</version></dependency><dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-job</artifactId><version>6.1.0</version></dependency><dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-job</artifactId></dependency></dependencies></project>",
                        spec -> spec.path("pom.xml")));
    }

    @Test
    void upgradesExistingPublicArtifactsForTheNewConfigurationApi() {
        rewriteRun(
                xml(
                        """
                        <project><dependencies>
                            <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-spring-boot</artifactId><version>4.0.0</version></dependency>
                            <dependency><groupId>ch.ubique.uetlib</groupId><artifactId>uetlib-spring-boot-security</artifactId><version>4.0.0</version></dependency>
                            <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-storage-spring-boot</artifactId><version>3.0.0</version></dependency>
                            <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-spring-boot-parent</artifactId><version>6.0.0</version></dependency>
                        </dependencies></project>
                        """,
                        """
                        <project><dependencies>
                            <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-spring-boot</artifactId><version>4.0.2</version></dependency>
                            <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-spring-boot-security</artifactId><version>4.0.2</version></dependency>
                            <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-storage-spring-boot</artifactId><version>3.0.2</version></dependency>
                            <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-spring-boot-parent</artifactId><version>6.0.0</version></dependency>
                        </dependencies></project>
                        """,
                        spec -> spec.path("pom.xml")));
    }

    @Override
    public void defaults(final RecipeSpec spec) {
        spec.recipe(new MigrateMavenCoordinates());
    }

    @Test
    void migratesIntermediateCoordinatesWithoutResettingVersions() {
        rewriteRun(
                xml(
                        """
                        <project>
                            <groupId>ch.ubique.uetlib</groupId><artifactId>uetlib-core</artifactId><version>4.2.0</version>
                            <dependencies><dependency>
                                <groupId>ch.ubique.uetlib</groupId><artifactId>uetlib-storage</artifactId><version>5.1.0</version>
                            </dependency></dependencies>
                        </project>
                        """,
                        """
                        <project>
                            <groupId>ch.uetlib</groupId><artifactId>uetlib-core</artifactId><version>4.2.0</version>
                            <dependencies><dependency>
                                <groupId>ch.uetlib</groupId><artifactId>uetlib-storage</artifactId><version>5.1.0</version>
                            </dependency></dependencies>
                        </project>
                        """,
                        spec -> spec.path("uetlib-core/pom.xml")));
    }

    @Test
    void migratesInternalParentManagedDependenciesAndVersionExpressions() {
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
                            <parent><groupId>ch.ubique.internal</groupId><artifactId>ubique-library-parent</artifactId><version>2026.11.0</version></parent>
                            <artifactId>private-application</artifactId>
                            <dependencyManagement><dependencies>
                                <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-self-bom</artifactId><version>6.0.2</version><type>pom</type><scope>import</scope></dependency>
                            </dependencies></dependencyManagement>
                            <dependencies>
                                <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-core</artifactId><version>3.0.1</version></dependency>
                                <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-storage</artifactId></dependency>
                                <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-inotify-java</artifactId><version>3.0.2</version></dependency>
                            </dependencies>
                        </project>
                        """,
                        spec -> spec.path("pom.xml")));
    }

    @Test
    void movesInternalBackendToInternalStarterParentAndPublicLibraries() {
        rewriteRun(
                xml(
                        """
                        <project>
                            <parent>
                                <groupId>ch.ubique</groupId>
                                <artifactId>uetlib-spring-boot-starter-parent</artifactId>
                                <version>${starter.version}</version>
                                <relativePath/>
                            </parent>
                            <groupId>ch.ubique.example</groupId>
                            <artifactId>example-ws</artifactId>
                            <dependencyManagement><dependencies>
                                <dependency><groupId>ch.ubique</groupId><artifactId>uetlib-bom</artifactId><version>2026.10.0</version><type>pom</type><scope>import</scope></dependency>
                            </dependencies></dependencyManagement>
                            <dependencies>
                                <dependency><groupId>ch.ubique</groupId><artifactId>uetlib-parent</artifactId><version>2026.10.1</version><type>pom</type></dependency>
                                <dependency><groupId>ch.ubique</groupId><artifactId>uetlib-spring-boot</artifactId></dependency>
                            </dependencies>
                        </project>
                        """,
                        """
                        <project>
                            <parent>
                                <groupId>ch.ubique.internal</groupId>
                                <artifactId>ubique-spring-boot-starter-parent</artifactId>
                                <version>4.1.2-2026.11.2</version>
                                <relativePath/>
                            </parent>
                            <groupId>ch.ubique.example</groupId>
                            <artifactId>example-ws</artifactId>
                            <dependencyManagement><dependencies>
                                <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-bom</artifactId><version>2026.11.4</version><type>pom</type><scope>import</scope></dependency>
                            </dependencies></dependencyManagement>
                            <dependencies>
                                <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-parent</artifactId><version>2026.11.1</version><type>pom</type></dependency>
                                <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-spring-boot</artifactId></dependency>
                            </dependencies>
                        </project>
                        """,
                        spec -> spec.path("pom.xml")));
    }

    @Test
    void movesInternalLibraryWithInheritedUbiqueGroupIdToInternalParent() {
        rewriteRun(
                xml(
                        """
                        <project>
                            <parent><groupId>ch.ubique</groupId><artifactId>uetlib-parent</artifactId><version>2026.10.1</version></parent>
                            <artifactId>example-lib</artifactId>
                            <dependencies>
                                <dependency><groupId>ch.ubique</groupId><artifactId>uetlib-core</artifactId><version>2.7.0</version></dependency>
                            </dependencies>
                        </project>
                        """,
                        """
                        <project>
                            <parent><groupId>ch.ubique.internal</groupId><artifactId>ubique-library-parent</artifactId><version>2026.11.0</version></parent>
                            <artifactId>example-lib</artifactId>
                            <dependencies>
                                <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-core</artifactId><version>3.0.1</version></dependency>
                            </dependencies>
                        </project>
                        """,
                        spec -> spec.path("pom.xml")));
    }

    @Test
    void movesIntermediatePublicParentsOfInternalProjectsKeepingTheirVersion() {
        rewriteRun(
                xml(
                        """
                        <project>
                            <parent><groupId>ch.uetlib</groupId><artifactId>uetlib-parent</artifactId><version>2026.11.1</version></parent>
                            <artifactId>example-lib</artifactId>
                        </project>
                        """,
                        """
                        <project>
                            <parent><groupId>ch.ubique.internal</groupId><artifactId>ubique-library-parent</artifactId><version>2026.11.1</version></parent>
                            <artifactId>example-lib</artifactId>
                        </project>
                        """,
                        spec -> spec.path("example-lib/pom.xml")),
                xml(
                        """
                        <project>
                            <parent><groupId>ch.uetlib</groupId><artifactId>uetlib-spring-boot-starter-parent</artifactId><version>4.1.1-2026.0.3-SNAPSHOT</version></parent>
                            <groupId>ch.ubique.example</groupId>
                            <artifactId>example-ws</artifactId>
                        </project>
                        """,
                        """
                        <project>
                            <parent><groupId>ch.ubique.internal</groupId><artifactId>ubique-spring-boot-starter-parent</artifactId><version>4.1.1-2026.0.3-SNAPSHOT</version></parent>
                            <groupId>ch.ubique.example</groupId>
                            <artifactId>example-ws</artifactId>
                        </project>
                        """,
                        spec -> spec.path("example-ws/pom.xml")));
    }

    @Test
    void keepsPublicParentsForOriginalUetlibProjects() {
        rewriteRun(
                xml(
                        """
                        <project>
                            <parent><groupId>ch.ubique</groupId><artifactId>uetlib-parent</artifactId><version>2026.10.1</version></parent>
                            <groupId>ch.ubique</groupId>
                            <artifactId>uetlib-core</artifactId>
                            <version>2.7.1-SNAPSHOT</version>
                        </project>
                        """,
                        """
                        <project>
                            <parent><groupId>ch.uetlib</groupId><artifactId>uetlib-parent</artifactId><version>2026.11.1</version></parent>
                            <groupId>ch.uetlib</groupId>
                            <artifactId>uetlib-core</artifactId>
                            <version>3.0.1</version>
                        </project>
                        """,
                        spec -> spec.path("uetlib-core/pom.xml")),
                xml(
                        """
                        <project>
                            <parent><groupId>ch.ubique</groupId><artifactId>uetlib-spring-boot-starter-parent</artifactId><version>4.1.1-2026.10.3</version></parent>
                            <artifactId>uetlib-testapp</artifactId>
                        </project>
                        """,
                        """
                        <project>
                            <parent><groupId>ch.uetlib</groupId><artifactId>uetlib-spring-boot-starter-parent</artifactId><version>4.1.2-2026.11.3</version></parent>
                            <artifactId>uetlib-testapp</artifactId>
                        </project>
                        """,
                        spec -> spec.path("uetlib-testapp/pom.xml")));
    }

    @Test
    void keepsPublicParentsForMigratedUetlibProjects() {
        rewriteRun(
                xml(
                        """
                        <project>
                            <parent><groupId>ch.uetlib</groupId><artifactId>uetlib-parent</artifactId><version>2026.0.3-SNAPSHOT</version></parent>
                            <artifactId>uetlib-geo-parent</artifactId>
                        </project>
                        """,
                        spec -> spec.path("uetlib-geo/pom.xml")),
                xml(
                        """
                        <project>
                            <parent><groupId>ch.uetlib</groupId><artifactId>uetlib-spring-boot-starter-parent</artifactId><version>4.1.1-2026.0.3-SNAPSHOT</version></parent>
                            <artifactId>uetlib-testapp</artifactId>
                        </project>
                        """,
                        spec -> spec.path("uetlib-testapp/pom.xml")));
    }

    @Test
    void keepsPublicParentsOfTheInternalWrapperParents() {
        rewriteRun(
                xml(
                        """
                        <project>
                            <parent><groupId>ch.uetlib</groupId><artifactId>uetlib-parent</artifactId><version>2026.11.1</version><relativePath/></parent>
                            <groupId>ch.ubique.internal</groupId>
                            <artifactId>ubique-library-parent</artifactId>
                            <version>2026.11.0</version>
                        </project>
                        """,
                        spec -> spec.path("ubique-library-parent/pom.xml")),
                xml(
                        """
                        <project>
                            <parent><groupId>ch.uetlib</groupId><artifactId>uetlib-spring-boot-starter-parent</artifactId><version>4.1.2-2026.11.3</version><relativePath/></parent>
                            <groupId>ch.ubique.internal</groupId>
                            <artifactId>ubique-spring-boot-starter-parent</artifactId>
                            <version>4.1.1-2026.11.0</version>
                        </project>
                        """,
                        spec -> spec.path("ubique-spring-boot-starter-parent/pom.xml")));
    }

    @Test
    void leavesInternalAndUnrelatedParentsUnchanged() {
        rewriteRun(
                xml(
                        """
                        <project>
                            <parent><groupId>ch.ubique.internal</groupId><artifactId>ubique-spring-boot-starter-parent</artifactId><version>4.1.1-2026.11.0</version></parent>
                            <artifactId>example-ws</artifactId>
                            <dependencies>
                                <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-spring-boot</artifactId><version>4.0.2</version></dependency>
                            </dependencies>
                        </project>
                        """,
                        spec -> spec.path("example-ws/pom.xml")),
                xml(
                        """
                        <project>
                            <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>4.1.1</version></parent>
                            <groupId>ch.ubique</groupId>
                            <artifactId>example-job</artifactId>
                        </project>
                        """,
                        spec -> spec.path("example-job/pom.xml")),
                xml(
                        """
                        <project>
                            <parent><groupId>ch.ubique.example</groupId><artifactId>example-parent</artifactId><version>1.0.0</version></parent>
                            <artifactId>example-module</artifactId>
                        </project>
                        """,
                        spec -> spec.path("example-module/pom.xml")));
    }

    @Test
    void redirectsOnlyTheProjectParentToInternalCoordinates() {
        rewriteRun(
                xml(
                        """
                        <project>
                            <parent><groupId>ch.ubique.example</groupId><artifactId>example-parent</artifactId><version>1.0.0</version></parent>
                            <artifactId>example-module</artifactId>
                            <build><plugins><plugin>
                                <artifactId>example-plugin</artifactId>
                                <configuration>
                                    <parent><groupId>ch.ubique</groupId><artifactId>uetlib-parent</artifactId><version>2026.10.1</version></parent>
                                </configuration>
                            </plugin></plugins></build>
                        </project>
                        """,
                        """
                        <project>
                            <parent><groupId>ch.ubique.example</groupId><artifactId>example-parent</artifactId><version>1.0.0</version></parent>
                            <artifactId>example-module</artifactId>
                            <build><plugins><plugin>
                                <artifactId>example-plugin</artifactId>
                                <configuration>
                                    <parent><groupId>ch.uetlib</groupId><artifactId>uetlib-parent</artifactId><version>2026.11.1</version></parent>
                                </configuration>
                            </plugin></plugins></build>
                        </project>
                        """,
                        spec -> spec.path("pom.xml")));
    }

    @Test
    void leavesAlreadyMigratedCoordinatesUnchanged() {
        rewriteRun(
                xml(
                        "<project><groupId>ch.uetlib</groupId><artifactId>uetlib-core</artifactId><version>3.0.1</version></project>",
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
                                <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-payment-datatrans</artifactId><version>3.0.2</version></dependency>
                            </dependencies>
                        </project>
                        """,
                        spec -> spec.path("pom.xml")));
    }
}
