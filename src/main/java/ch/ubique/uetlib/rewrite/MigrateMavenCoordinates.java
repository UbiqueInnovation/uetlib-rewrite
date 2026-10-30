package ch.ubique.uetlib.rewrite;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Properties;
import org.openrewrite.ExecutionContext;
import org.openrewrite.FindSourceFiles;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

/** Updates known uetlib coordinates without resolving the old private artifacts. */
public class MigrateMavenCoordinates extends Recipe {
    private static final Properties RELEASES = releases();

    private static Properties releases() {
        Properties versions = new Properties();
        try (InputStream stream =
                MigrateMavenCoordinates.class.getResourceAsStream(
                        "/ch/ubique/uetlib/rewrite/migration-versions.properties")) {
            if (stream == null) {
                throw new IllegalStateException("Missing uetlib migration release map");
            }
            versions.load(stream);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load uetlib migration release map", exception);
        }
        return versions;
    }

    @Override
    public String getDisplayName() {
        return "Migrate uetlib Maven coordinates";
    }

    @Override
    public String getDescription() {
        return "Move known uetlib artifacts to ch.ubique.uetlib and the first public migration releases, including parents, BOMs and processor paths.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new FindSourceFiles("**/pom.xml"),
                new XmlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext context) {
                        Xml.Tag updated = super.visitTag(tag, context);
                        // The old model and service modules are now one dependency. Collapse
                        // duplicates only when the entire migrated declaration is identical.
                        if ("dependencies".equals(updated.getName())
                                && updated.getContent() != null) {
                            java.util.Set<String> seen = new java.util.HashSet<>();
                            java.util.List<Content> content = new ArrayList<>();
                            for (Content child : updated.getContent()) {
                                if (child instanceof Xml.Tag dependency
                                        && "ch.ubique.uetlib"
                                                .equals(
                                                        dependency
                                                                .getChildValue("groupId")
                                                                .orElse(null))
                                        && "uetlib-payment-datatrans"
                                                .equals(
                                                        dependency
                                                                .getChildValue("artifactId")
                                                                .orElse(null))) {
                                    String key =
                                            dependency
                                                    .printTrimmed(getCursor())
                                                    .replaceAll("\\s+", "");
                                    if (!seen.add(key)) continue;
                                }
                                content.add(child);
                            }
                            if (content.size() != updated.getContent().size()) {
                                updated = updated.withContent(content);
                            }
                        }
                        if ("dependency".equals(updated.getName())
                                && "ch.ubique.datatrans"
                                        .equals(updated.getChildValue("groupId").orElse(null))
                                && java.util.Set.of(
                                                "datatrans-api-client-model",
                                                "datatrans-api-client-service")
                                        .contains(updated.getChildValue("artifactId").orElse(""))) {
                            updated =
                                    updated.withChildValue("groupId", "ch.ubique.uetlib")
                                            .withChildValue(
                                                    "artifactId", "uetlib-payment-datatrans");
                            if (updated.getChild("version").isPresent()) {
                                updated =
                                        updated.withChildValue(
                                                "version",
                                                RELEASES.getProperty("uetlib-payment-datatrans"));
                            }
                            return updated;
                        }
                        if (!"ch.ubique".equals(updated.getChildValue("groupId").orElse(null))) {
                            return updated;
                        }
                        String artifact = updated.getChildValue("artifactId").orElse("");
                        String version = RELEASES.getProperty(artifact);
                        if (version == null) {
                            return updated;
                        }
                        updated = updated.withChildValue("groupId", "ch.ubique.uetlib");
                        if (updated.getChild("version").isPresent()) {
                            updated = updated.withChildValue("version", version);
                        }
                        return updated;
                    }
                });
    }
}
