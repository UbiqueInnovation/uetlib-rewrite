package ch.uetlib.rewrite;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

/** Checks the whole reactor before any migration edits can run. */
public class RequireUetlibParent extends ScanningRecipe<Map<Path, Xml.Tag>> {
    static boolean isUetlibParent(final String group, final String artifact) {
        return (Set.of("ch.ubique", "ch.ubique.uetlib", "ch.uetlib").contains(group)
                        && Set.of("uetlib-spring-boot-starter-parent", "uetlib-parent")
                                .contains(artifact))
                || ("ch.ubique.internal".equals(group)
                        && Set.of("ubique-spring-boot-starter-parent", "ubique-library-parent")
                                .contains(artifact));
    }

    @Override
    public String getDisplayName() {
        return "Require a supported uetlib parent";
    }

    @Override
    public String getDescription() {
        return "Abort the combined migration before editing if any Maven project does not inherit a supported uetlib parent or Spring Boot's starter-parent 4.0.0 or newer, directly or through a local reactor parent.";
    }

    @Override
    public Map<Path, Xml.Tag> getInitialValue(final ExecutionContext ctx) {
        return new HashMap<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(final Map<Path, Xml.Tag> projects) {
        return new XmlIsoVisitor<>() {
            @Override
            public Xml.Document visitDocument(
                    final Xml.Document document, final ExecutionContext ctx) {
                if ("pom.xml".equals(document.getSourcePath().getFileName().toString())) {
                    projects.put(document.getSourcePath().normalize(), document.getRoot());
                }
                return document;
            }
        };
    }

    private static boolean inheritsUetlib(
            final Path path, final Map<Path, Xml.Tag> projects, final Set<Path> visited) {
        if (!visited.add(path)) {
            return false;
        }
        final var project = projects.get(path);
        if (project == null) {
            return false;
        }
        final var parent = project.getChild("parent").orElse(null);
        if (parent == null) {
            return false;
        }
        final var group = parent.getChildValue("groupId").orElse("");
        final var artifact = parent.getChildValue("artifactId").orElse("");
        if (isUetlibParent(group, artifact)
                || MigrateSpringBootStarterParent.isSpringBootParent(
                        group, artifact, parent.getChildValue("version").orElse(""))) {
            return true;
        }
        final var relativePath =
                parent.getChild("relativePath")
                        .map(t -> t.getValue().orElse(""))
                        .orElse("../pom.xml");
        if (relativePath.isBlank()) {
            return false;
        }
        var parentPath = path.resolveSibling(relativePath).normalize();
        if (!"pom.xml".equals(parentPath.getFileName().toString())) {
            parentPath = parentPath.resolve("pom.xml");
        }
        final var localParent = projects.get(parentPath);
        if (localParent == null
                || !artifact.equals(localParent.getChildValue("artifactId").orElse(""))
                || !group.equals(
                        localParent
                                .getChildValue("groupId")
                                .or(() ->
                                        localParent
                                                .getChild("parent")
                                                .flatMap(p -> p.getChildValue("groupId")))
                                .orElse(""))
                || !parent.getChildValue("version")
                        .equals(
                                localParent
                                        .getChildValue("version")
                                        .or(() ->
                                                localParent
                                                        .getChild("parent")
                                                        .flatMap(p ->
                                                                p.getChildValue("version"))))) {
            return false;
        }
        return inheritsUetlib(parentPath, projects, visited);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(final Map<Path, Xml.Tag> projects) {
        final var invalid =
                projects.keySet().stream()
                        .filter(path -> !inheritsUetlib(path, projects, new HashSet<>()))
                        .sorted()
                        .toList();
        if (!projects.isEmpty() && invalid.isEmpty()) {
            return TreeVisitor.noop();
        }
        return new TreeVisitor<>() {
            @Override
            public Tree preVisit(final Tree tree, final ExecutionContext ctx) {
                // The scheduler normally records recipe exceptions and continues. Stop
                // every edit visitor and attach the error to a real source file, so the
                // Maven plugin fails before writing any results.
                ctx.putMessage(PANIC, true);
                throw new IllegalStateException(
                        "MigrateToPublicUetlib requires uetlib-parent, uetlib-spring-boot-starter-parent, "
                                + "their ch.ubique.internal wrappers ubique-library-parent or ubique-spring-boot-starter-parent, "
                                + "or org.springframework.boot:spring-boot-starter-parent 4.0.0 or newer. Unsupported POMs: "
                                + (projects.isEmpty() ? "no pom.xml found" : invalid));
            }
        };
    }
}
