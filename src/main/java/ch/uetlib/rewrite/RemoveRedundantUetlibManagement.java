package ch.uetlib.rewrite;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.maven.MavenDownloadingException;
import org.openrewrite.maven.internal.MavenPomDownloader;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.Pom;
import org.openrewrite.maven.tree.ResolvedPom;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

/** Removes whole, plain management declarations, rather than leaving versionless entries. */
public class RemoveRedundantUetlibManagement extends Recipe {
    @Override
    public String getDisplayName() {
        return "Remove redundant uetlib dependency management";
    }

    @Override
    public String getDescription() {
        return "Remove plain uetlib dependencyManagement entries whenever the parent manages the artifact, regardless of the pinned version. Keep BOM imports, profiles, exclusions and other custom settings.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new XmlIsoVisitor<>() {
            @Override
            public Xml.Document visitDocument(
                    final Xml.Document document, final ExecutionContext ctx) {
                final var resolution =
                        document.getMarkers().findFirst(MavenResolutionResult.class).orElse(null);
                final var dependencies =
                        document.getRoot()
                                .getChild("dependencyManagement")
                                .flatMap(dm -> dm.getChild("dependencies"))
                                .orElse(null);
                // XML-only parsing intentionally supports migration of unavailable private
                // artifacts. Never guess inherited versions when the Maven model is absent.
                if (resolution == null || dependencies == null) {
                    return document;
                }
                final var counts =
                        dependencies.getChildren().stream()
                                .collect(
                                        groupingBy(
                                                RemoveRedundantUetlibManagement::key, counting()));
                final var candidates =
                        dependencies.getChildren().stream()
                                .filter(RemoveRedundantUetlibManagement::isPlainUetlibDependency)
                                .filter(tag -> counts.get(key(tag)) == 1)
                                .toList();
                if (candidates.isEmpty()) {
                    return document;
                }
                final var pom = resolution.getPom();
                final var downloader =
                        new MavenPomDownloader(
                                resolution.getProjectPoms(),
                                ctx,
                                resolution.getMavenSettings(),
                                resolution.getActiveProfiles());
                try {
                    // Resolve with all local management and profiles removed to establish
                    // what the parent actually supplies, not the override itself.
                    final var inherited =
                            pom.getRequested()
                                    .withDependencyManagement(List.of())
                                    .withProfiles(List.of())
                                    .resolve(
                                            pom.getActiveProfiles(),
                                            downloader,
                                            pom.getInitialRepositories(),
                                            ctx);
                    final Set<String> remove = new HashSet<>();
                    for (final var tag : candidates) {
                        if (isManagedByParent(tag, inherited)) {
                            remove.add(key(tag));
                        }
                    }
                    if (remove.isEmpty()) {
                        return document;
                    }
                    final var requested = withoutEntries(pom, remove);
                    final var updatedModel =
                            requested.resolve(
                                    pom.getActiveProfiles(),
                                    downloader,
                                    pom.getInitialRepositories(),
                                    ctx);
                    final var updatedDependencies =
                            dependencies.withContent(
                                    dependencies.getContent().stream()
                                            .filter(
                                                    c ->
                                                            !(c instanceof Xml.Tag tag)
                                                                    || !remove.contains(key(tag)))
                                            .toList());
                    final var management =
                            document.getRoot().getChild("dependencyManagement").orElseThrow();
                    final var root =
                            replaceChild(
                                    document.getRoot(),
                                    management,
                                    replaceChild(management, dependencies, updatedDependencies));
                    return document.withRoot(root)
                            .withMarkers(
                                    document.getMarkers()
                                            .setByType(resolution.withPom(updatedModel)));
                } catch (final MavenDownloadingException exception) {
                    // Leave declarations intact when redundancy cannot be established.
                    return exception.warn(document);
                }
            }
        };
    }

    private static Pom withoutEntries(final ResolvedPom pom, final Set<String> remove) {
        return pom.getRequested()
                .withDependencyManagement(
                        pom.getRequested().getDependencyManagement().stream()
                                .filter(
                                        d ->
                                                !remove.contains(
                                                        pom.getValue(d.getGroupId())
                                                                + ":"
                                                                + pom.getValue(d.getArtifactId())))
                                .toList());
    }

    private static Xml.Tag replaceChild(
            final Xml.Tag parent, final Xml.Tag before, final Xml.Tag after) {
        return parent.withContent(
                parent.getContent().stream().map(c -> c == before ? after : c).toList());
    }

    private static boolean isPlainUetlibDependency(final Xml.Tag tag) {
        return "dependency".equals(tag.getName())
                && Set.of("ch.ubique", "ch.ubique.uetlib", "ch.uetlib")
                        .contains(tag.getChildValue("groupId").orElse(""))
                && tag.getChildValue("artifactId").orElse("").startsWith("uetlib-")
                && tag.getChild("version").isPresent()
                && tag.getAttributes().isEmpty()
                && tag.getChildren().size() == 3
                && tag.getContent().stream().allMatch(Xml.Tag.class::isInstance);
    }

    private static String key(final Xml.Tag tag) {
        return tag.getChildValue("groupId").orElse("")
                + ":"
                + tag.getChildValue("artifactId").orElse("");
    }

    private static boolean isManagedByParent(final Xml.Tag tag, final ResolvedPom inherited) {
        final var managed =
                inherited.getManagedDependency(
                        tag.getChildValue("groupId").orElse(""),
                        tag.getChildValue("artifactId").orElse(""),
                        "jar",
                        null);
        return managed != null
                && (managed.getScope() == null
                        || managed.getScope() == org.openrewrite.maven.tree.Scope.Compile)
                && (managed.getExclusions() == null || managed.getExclusions().isEmpty());
    }
}
