package ch.uetlib.rewrite;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import org.openrewrite.ExecutionContext;
import org.openrewrite.FindSourceFiles;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

/** Updates known uetlib coordinates without resolving the old private artifacts. */
public class MigrateMavenCoordinates extends Recipe {
    private static final Properties RELEASES = releases();
    private static final Set<String> UETLIB_GROUPS =
            Set.of("ch.ubique", "ch.ubique.uetlib", "ch.uetlib");
    private static final String INTERNAL_GROUP = "ch.ubique.internal";
    private static final Set<String> PROPERTY_API_ARTIFACTS =
            Set.of(
                    "uetlib-spring-boot-parent",
                    "uetlib-spring-boot",
                    "uetlib-spring-boot-security",
                    "uetlib-spring-boot-coverage",
                    "uetlib-storage-spring-boot",
                    "uetlib-job");

    private static boolean beforePropertyApi(final String current, final String required) {
        final var version = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)").matcher(current);
        if (!version.find()) {
            return true;
        }
        final var minimum = required.split("\\.");
        for (var i = 0; i < 3; i++) {
            final var comparison =
                    new BigInteger(version.group(i + 1)).compareTo(new BigInteger(minimum[i]));
            if (comparison != 0) {
                return comparison < 0;
            }
        }
        return false;
    }

    // Public parents and the Ubique-internal wrappers that replace them outside uetlib.
    private static final Map<String, String> INTERNAL_PARENTS =
            Map.of(
                    "uetlib-parent", "ubique-library-parent",
                    "uetlib-spring-boot-starter-parent", "ubique-spring-boot-starter-parent");
    private static final XPathMatcher PROJECT_PARENT = new XPathMatcher("/project/parent");

    private static Properties releases() {
        final var versions = new Properties();
        try (final var stream =
                MigrateMavenCoordinates.class.getResourceAsStream(
                        "/ch/uetlib/rewrite/migration-versions.properties")) {
            if (stream == null) {
                throw new IllegalStateException("Missing uetlib migration release map");
            }
            versions.load(stream);
        } catch (final IOException exception) {
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
        return "Move known uetlib artifacts to ch.uetlib and the first public migration releases, including parents, BOMs and processor paths. Projects outside uetlib move their uetlib parent to the matching Ubique-internal parent.";
    }

    /**
     * Uetlib's own projects ({@code ch.ubique[.uetlib]:uetlib-*}) and the internal wrapper parents
     * keep the public parents. The groupId may be inherited from the parent.
     */
    private static boolean isInternalConsumer(final Xml.Tag project) {
        final var artifact = project.getChildValue("artifactId").orElse("");
        final var group =
                project.getChildValue("groupId")
                        .or(
                                () ->
                                        project.getChild("parent")
                                                .flatMap(p -> p.getChildValue("groupId")))
                        .orElse("");
        final var uetlib = UETLIB_GROUPS.contains(group) && artifact.startsWith("uetlib-");
        final var wrapper =
                INTERNAL_GROUP.equals(group) && INTERNAL_PARENTS.containsValue(artifact);
        return !uetlib && !wrapper;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new FindSourceFiles("**/pom.xml"),
                new XmlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Xml.Tag visitTag(final Xml.Tag tag, final ExecutionContext context) {
                        var updated = super.visitTag(tag, context);
                        // The old model and service modules are now one dependency. Collapse
                        // duplicates only when the entire migrated declaration is identical.
                        if ("dependencies".equals(updated.getName())
                                && updated.getContent() != null) {
                            final Set<String> seen = new HashSet<>();
                            final List<Content> content = new ArrayList<>();
                            for (final var child : updated.getContent()) {
                                if (child instanceof final Xml.Tag dependency
                                        && "ch.uetlib"
                                                .equals(
                                                        dependency
                                                                .getChildValue("groupId")
                                                                .orElse(null))
                                        && "uetlib-payment-datatrans"
                                                .equals(
                                                        dependency
                                                                .getChildValue("artifactId")
                                                                .orElse(null))) {
                                    final var key =
                                            dependency
                                                    .printTrimmed(getCursor())
                                                    .replaceAll("\\s+", "");
                                    if (!seen.add(key)) {
                                        continue;
                                    }
                                }
                                content.add(child);
                            }
                            if (content.size() != updated.getContent().size()) {
                                updated = updated.withContent(content);
                            }
                        }
                        final var group = updated.getChildValue("groupId").orElse(null);
                        final var internalParent =
                                INTERNAL_PARENTS.get(
                                        updated.getChildValue("artifactId").orElse(""));
                        if (internalParent != null
                                && UETLIB_GROUPS.contains(group)
                                && PROJECT_PARENT.matches(getCursor())
                                && isInternalConsumer(
                                        getCursor()
                                                .firstEnclosingOrThrow(Xml.Document.class)
                                                .getRoot())) {
                            updated =
                                    updated.withChildValue("groupId", INTERNAL_GROUP)
                                            .withChildValue("artifactId", internalParent);
                            // Wrapper versions equal the public versions they wrap, so
                            // intermediate ch.ubique.uetlib parents keep their version.
                            if ("ch.ubique".equals(group)
                                    && updated.getChild("version").isPresent()) {
                                updated =
                                        updated.withChildValue(
                                                "version",
                                                RELEASES.getProperty("internal." + internalParent));
                            }
                            return updated;
                        }
                        if ("dependency".equals(updated.getName())
                                && UETLIB_GROUPS.contains(group)
                                && "uetlib-spring-boot-geo"
                                        .equals(updated.getChildValue("artifactId").orElse(""))) {
                            updated =
                                    updated.withChildValue("groupId", "ch.uetlib")
                                            .withChildValue("artifactId", "uetlib-geo-spring-boot");
                            if ("ch.ubique".equals(group)
                                    && updated.getChild("version").isPresent()) {
                                updated =
                                        updated.withChildValue(
                                                "version",
                                                RELEASES.getProperty("uetlib-geo-spring-boot"));
                            }
                            return updated;
                        }
                        if ("dependency".equals(updated.getName())
                                && "ch.ubique".equals(group)
                                && "inotify"
                                        .equals(updated.getChildValue("artifactId").orElse(""))) {
                            updated =
                                    updated.withChildValue("groupId", "ch.uetlib")
                                            .withChildValue("artifactId", "uetlib-inotify-java");
                            if (updated.getChild("version").isPresent()) {
                                updated =
                                        updated.withChildValue(
                                                "version",
                                                RELEASES.getProperty("uetlib-inotify-java"));
                            }
                            return updated;
                        }
                        if ("dependency".equals(updated.getName())
                                && "ch.ubique.datatrans"
                                        .equals(updated.getChildValue("groupId").orElse(null))
                                && Set.of(
                                                "datatrans-api-client-model",
                                                "datatrans-api-client-service")
                                        .contains(updated.getChildValue("artifactId").orElse(""))) {
                            updated =
                                    updated.withChildValue("groupId", "ch.uetlib")
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
                        final var propertyArtifact = updated.getChildValue("artifactId").orElse("");
                        if (PROPERTY_API_ARTIFACTS.contains(propertyArtifact)
                                && UETLIB_GROUPS.contains(group)
                                && ("dependency".equals(updated.getName())
                                        || PROJECT_PARENT.matches(getCursor()))) {
                            if (!"ch.uetlib".equals(group)) {
                                updated = updated.withChildValue("groupId", "ch.uetlib");
                            }
                            final var current = updated.getChildValue("version").orElse(null);
                            if (current != null) {
                                if (beforePropertyApi(
                                        current, RELEASES.getProperty(propertyArtifact))) {
                                    updated =
                                            updated.withChildValue(
                                                    "version",
                                                    RELEASES.getProperty(propertyArtifact));
                                }
                            }
                            return updated;
                        }
                        if (!Set.of("ch.ubique", "ch.ubique.uetlib")
                                .contains(updated.getChildValue("groupId").orElse(""))) {
                            return updated;
                        }
                        final var artifact = updated.getChildValue("artifactId").orElse("");
                        final var version = RELEASES.getProperty(artifact);
                        if (version == null) {
                            return updated;
                        }
                        updated = updated.withChildValue("groupId", "ch.uetlib");
                        if ("ch.ubique".equals(group) && updated.getChild("version").isPresent()) {
                            updated = updated.withChildValue("version", version);
                        }
                        return updated;
                    }
                });
    }
}
