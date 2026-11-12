package ch.uetlib.rewrite;

import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class RemoveRedundantUetlibManagementTest implements RewriteTest {
    private static final String PARENT =
            """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>example</groupId><artifactId>parent</artifactId><version>1</version><packaging>pom</packaging>
                <dependencyManagement><dependencies>
                    <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-core</artifactId><version>3.0.2</version></dependency>
                    <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-job</artifactId><version>6.0.3</version></dependency>
                    <dependency><groupId>example</groupId><artifactId>other</artifactId><version>1</version></dependency>
                </dependencies></dependencyManagement>
            </project>
            """;
    private static final String HEADER =
            """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <parent><groupId>example</groupId><artifactId>parent</artifactId><version>1</version></parent>
                <artifactId>application</artifactId>
            """;

    @Override
    public void defaults(final RecipeSpec spec) {
        spec.recipe(new RemoveRedundantUetlibManagement());
    }

    @Test
    void removesMatchingAndNewerPinsButPreservesUnrelatedManagement() {
        rewriteRun(
                pomXml(PARENT, spec -> spec.path("pom.xml")),
                pomXml(
                        HEADER
                                + """
                    <properties><core.version>3.0.2</core.version></properties>
                    <dependencyManagement><dependencies>
                        <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-core</artifactId><version>${core.version}</version></dependency>
                        <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-job</artifactId><version>7.0.0</version></dependency>
                        <dependency><groupId>example</groupId><artifactId>other</artifactId><version>1</version></dependency>
                    </dependencies></dependencyManagement>
                </project>
                """,
                        HEADER
                                + """
                    <properties><core.version>3.0.2</core.version></properties>
                    <dependencyManagement><dependencies>
                        <dependency><groupId>example</groupId><artifactId>other</artifactId><version>1</version></dependency>
                    </dependencies></dependencyManagement>
                </project>
                """,
                        spec -> spec.path("app/pom.xml")));
    }

    @Test
    void removesPinsRegardlessOfVersionIncludingPrereleasesRangesAndExpressions() {
        for (final var version :
                java.util.List.of(
                        "3.0.1",
                        "3.0.2-alpha.1",
                        "3.0.2",
                        "3.0.10",
                        "[3.0,4.0)",
                        "${core.version}")) {
            final var before =
                    HEADER
                            + "<properties><core.version>3.0.10</core.version></properties>"
                            + "<dependencyManagement><dependencies><dependency>"
                            + "<groupId>ch.uetlib</groupId><artifactId>uetlib-core</artifactId><version>"
                            + version
                            + "</version></dependency></dependencies></dependencyManagement></project>";
            final var after =
                    HEADER
                            + "<properties><core.version>3.0.10</core.version></properties>"
                            + "<dependencyManagement><dependencies></dependencies></dependencyManagement></project>";
            rewriteRun(
                    pomXml(PARENT, spec -> spec.path("pom.xml")),
                    pomXml(before, after, spec -> spec.path("app/pom.xml")));
        }
    }

    @Test
    void keepsCustomDeclarationsAndUnmanagedArtifacts() {
        rewriteRun(
                pomXml(PARENT, spec -> spec.path("pom.xml")),
                pomXml(
                        HEADER
                                + """
                    <dependencyManagement><dependencies>
                        <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-core</artifactId><version>3.0.2</version><exclusions><exclusion><groupId>example</groupId><artifactId>excluded</artifactId></exclusion></exclusions></dependency>
                        <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-job</artifactId><version>6.0.3</version><classifier>tests</classifier></dependency>
                        <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-unmanaged</artifactId><version>1</version></dependency>
                    </dependencies></dependencyManagement>
                </project>
                """,
                        spec -> spec.path("app/pom.xml")));
    }

    @Test
    void preservesEffectiveVersionAndLocalBomImports() {
        org.openrewrite.maven.Assertions.withLocalRepository(
                """
                        <project>
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>example</groupId><artifactId>override-bom</artifactId><version>1</version><packaging>pom</packaging>
                            <dependencyManagement><dependencies>
                                <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-core</artifactId><version>3.0.1</version></dependency>
                            </dependencies></dependencyManagement>
                        </project>
                        """,
                () ->
                        rewriteRun(
                                pomXml(PARENT, spec -> spec.path("pom.xml")),
                                pomXml(
                                        HEADER
                                                + """
                    <dependencyManagement><dependencies>
                        <dependency><groupId>example</groupId><artifactId>override-bom</artifactId><version>1</version><type>pom</type><scope>import</scope></dependency>
                        <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-core</artifactId><version>3.0.2</version></dependency>
                    </dependencies></dependencyManagement>
                </project>
                """,
                                        HEADER
                                                + """
                    <dependencyManagement><dependencies>
                        <dependency><groupId>example</groupId><artifactId>override-bom</artifactId><version>1</version><type>pom</type><scope>import</scope></dependency>
                    </dependencies></dependencyManagement>
                </project>
                """,
                                        spec -> spec.path("app/pom.xml"))));
    }

    @Test
    void keepsManagementWithoutResolvedParentMetadata() {
        rewriteRun(
                xml(
                        HEADER
                                + """
                    <dependencyManagement><dependencies>
                        <dependency><groupId>ch.uetlib</groupId><artifactId>uetlib-core</artifactId><version>3.0.2</version></dependency>
                    </dependencies></dependencyManagement>
                </project>
                """,
                        spec -> spec.path("pom.xml")));
    }
}
