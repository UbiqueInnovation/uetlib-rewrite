package ch.uetlib.rewrite;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.openrewrite.ExecutionContext;
import org.openrewrite.FindSourceFiles;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.maven.MavenTagInsertionComparator;
import org.openrewrite.xml.AddToTagVisitor;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

/**
 * Moves projects from Spring Boot's own parent to the uetlib starter-parent, which supplies the
 * uetlib BOMs, Java version, encoding, plugin, JaCoCo and Sonar settings these projects declared
 * locally.
 */
public class MigrateSpringBootStarterParent extends ScanningRecipe<Map<String, String>> {
    static final String SPRING_BOOT_GROUP = "org.springframework.boot";
    static final String SPRING_BOOT_PARENT = "spring-boot-starter-parent";
    private static final String UETLIB_PARENT = "uetlib-spring-boot-starter-parent";
    private static final Set<String> UETLIB_GROUPS =
            Set.of("ch.ubique", "ch.ubique.uetlib", "ch.uetlib");
    private static final Set<String> REMOVED_PROPERTIES =
            Set.of(
                    "java.version",
                    "maven.compiler.release",
                    "project.build.sourceEncoding",
                    "jacoco.version");
    private static final String COVERAGE_MODULE = "sonar.coverage.module";
    private static final String NULL_MARKING = "explicit-null-marking-level";
    // Plugins the parent manages or configures, by artifactId and groupId.
    private static final Map<String, String> MANAGED_PLUGINS =
            Map.of(
                    "spring-boot-maven-plugin",
                    SPRING_BOOT_GROUP,
                    "jib-maven-plugin",
                    "com.google.cloud.tools");
    private static final Map<String, String> PARENT_PLUGINS =
            Map.of("jacoco-maven-plugin", "org.jacoco");
    // The libraries the self-bom manages, including the former geo Spring Boot artifact name.
    private static final Set<String> MANAGED_ARTIFACTS =
            Set.of(
                    "uetlib-core",
                    "uetlib-db",
                    "uetlib-errorprone",
                    "uetlib-geo",
                    "uetlib-geo-db",
                    "uetlib-geo-job",
                    "uetlib-geo-spring-boot",
                    "uetlib-spring-boot-geo",
                    "uetlib-inotify-java",
                    "uetlib-job",
                    "uetlib-monitoring",
                    "uetlib-payment",
                    "uetlib-payment-datatrans",
                    "uetlib-payment-postfinance",
                    "uetlib-spring-boot",
                    "uetlib-spring-boot-security",
                    "uetlib-spring-cloud",
                    "uetlib-spring-cloud-security",
                    "uetlib-storage",
                    "uetlib-storage-spring-boot",
                    "uetlib-webp",
                    "uetlib-test",
                    "uetlib-spring-boot-test",
                    "uetlib-testcontainers",
                    "uetlib-testcontainers-influxdb",
                    "uetlib-testcontainers-kafka",
                    "uetlib-testcontainers-mariadb",
                    "uetlib-testcontainers-mongodb",
                    "uetlib-testcontainers-mysql",
                    "uetlib-testcontainers-postgresql",
                    "uetlib-testcontainers-redis");
    private static final String COVERAGE_BUILD =
            """
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.jacoco</groupId>
                        <artifactId>jacoco-maven-plugin</artifactId>
                        <executions>
                            <execution>
                                <id>report-aggregate</id>
                                <phase>verify</phase>
                                <goals>
                                    <goal>report-aggregate</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>\
            """;

    /**
     * The uetlib starter-parent starts with Spring Boot 4.0.0; projects on older Spring Boot
     * versions must upgrade Spring Boot first.
     */
    static boolean isSpringBootParent(
            final String group, final String artifact, final String version) {
        if (!SPRING_BOOT_GROUP.equals(group) || !SPRING_BOOT_PARENT.equals(artifact)) {
            return false;
        }
        final var parsed =
                Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:[.-]([0-9A-Za-z][0-9A-Za-z.-]*))?$")
                        .matcher(version);
        if (!parsed.matches()) {
            return false;
        }
        final var major = new BigInteger(parsed.group(1)).compareTo(BigInteger.valueOf(4));
        if (major != 0) {
            return major > 0;
        }
        if (new BigInteger(parsed.group(2)).signum() > 0
                || new BigInteger(parsed.group(3)).signum() > 0) {
            return true;
        }
        final var qualifier = parsed.group(4);
        return qualifier == null
                || Set.of("RELEASE", "FINAL", "GA")
                        .contains(qualifier.toUpperCase(java.util.Locale.ROOT));
    }

    /** The uetlib starter-parent itself inherits Spring Boot's parent. */
    private static boolean isMigrated(final Xml.Tag root) {
        final var parent = root.getChild("parent").orElse(null);
        return parent != null
                && isSpringBootParent(
                        parent.getChildValue("groupId").orElse(""),
                        parent.getChildValue("artifactId").orElse(""),
                        parent.getChildValue("version").orElse(""))
                && !UETLIB_PARENT.equals(root.getChildValue("artifactId").orElse(""));
    }

    private static String coverageModule(final String artifact) {
        return artifact.replace("-backend", "-coverage");
    }

    @Override
    public String getDisplayName() {
        return "Migrate Spring Boot's starter-parent to the uetlib starter-parent";
    }

    @Override
    public String getDescription() {
        return "Replace Spring Boot's starter-parent (4.0.0 or newer) with the uetlib starter-parent. Remove redundant uetlib management and module dependency versions, parent-supplied properties and empty comments. Keep custom Spring Boot and Jib plugin configuration while removing versions, executions and mainClass. Configure coverage aggregation and report missing explicit null marking as warnings.";
    }

    @Override
    public Map<String, String> getInitialValue(final ExecutionContext ctx) {
        return new HashMap<>();
    }

    /** Collects the coverage module of each migrated project, mapped to that project. */
    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(final Map<String, String> coverage) {
        return Preconditions.check(
                new FindSourceFiles("**/pom.xml"),
                new XmlIsoVisitor<>() {
                    @Override
                    public Xml.Document visitDocument(
                            final Xml.Document document, final ExecutionContext ctx) {
                        final var root = document.getRoot();
                        if (isMigrated(root)) {
                            final var artifact = root.getChildValue("artifactId").orElse("");
                            coverage.put(coverageModule(artifact), artifact);
                        }
                        return document;
                    }
                });
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(final Map<String, String> coverage) {
        return Preconditions.check(
                new FindSourceFiles("**/pom.xml"),
                new XmlIsoVisitor<>() {
                    @Override
                    public Xml.Document visitDocument(
                            final Xml.Document document, final ExecutionContext ctx) {
                        var root = document.getRoot();
                        final var parent = root.getChild("parent").orElse(null);
                        final var artifact = root.getChildValue("artifactId").orElse("");
                        if (parent != null
                                && coverage.containsKey(artifact)
                                && coverage.get(artifact)
                                        .equals(parent.getChildValue("artifactId").orElse(""))) {
                            return withCoverageBuild(document);
                        }
                        if (parent != null
                                && coverage.containsValue(
                                        parent.getChildValue("artifactId").orElse(""))) {
                            final var updated =
                                    withBarePlugins(
                                            withoutUetlibVersions(withoutUetlibManagement(root)));
                            return updated == root ? document : document.withRoot(updated);
                        }
                        if (!isMigrated(root)) {
                            return document;
                        }
                        // Pin the public parent; MigrateMavenCoordinates then moves projects
                        // outside uetlib to the Ubique-internal parent.
                        root =
                                replace(
                                        root,
                                        parent,
                                        parent.withChildValue("groupId", "ch.uetlib")
                                                .withChildValue("artifactId", UETLIB_PARENT)
                                                .withChildValue(
                                                        "version",
                                                        MigrateMavenCoordinates.RELEASES
                                                                .getProperty(UETLIB_PARENT)));
                        root = withoutUetlibVersions(withoutUetlibManagement(root));
                        root = withBarePlugins(withoutParentPlugins(root));
                        final var coverageModule = coverageModule(artifact);
                        final var properties = root.getChild("properties");
                        if (properties.isPresent()) {
                            final var updated = updateProperties(properties.get(), coverageModule);
                            root =
                                    replace(
                                            root,
                                            properties.get(),
                                            withAdded(
                                                    updated,
                                                    missingProperties(updated, coverageModule)));
                        } else {
                            final var added =
                                    Xml.Tag.build(
                                            "<properties>\n"
                                                    + property(COVERAGE_MODULE, coverageModule)
                                                    + "\n"
                                                    + property(NULL_MARKING, "WARN")
                                                    + "\n</properties>");
                            doAfterVisit(
                                    new AddToTagVisitor<>(
                                            root,
                                            added,
                                            new MavenTagInsertionComparator(root.getChildren())));
                        }
                        return document.withRoot(root);
                    }

                    /**
                     * The coverage module only aggregates the reports; the parent configures
                     * everything else.
                     */
                    private Xml.Document withCoverageBuild(final Xml.Document document) {
                        var root = document.getRoot();
                        final var build = root.getChild("build").orElse(null);
                        if (build != null) {
                            if (build.printTrimmed(getCursor())
                                    .replaceAll("\\s+", "")
                                    .equals(COVERAGE_BUILD.replaceAll("\\s+", ""))) {
                                return document;
                            }
                            root =
                                    root.withContent(
                                            root.getContent().stream()
                                                    .filter(c -> c != build)
                                                    .toList());
                        }
                        doAfterVisit(
                                new AddToTagVisitor<>(
                                        root,
                                        Xml.Tag.build(COVERAGE_BUILD),
                                        new MavenTagInsertionComparator(root.getChildren())));
                        return document.withRoot(root);
                    }
                });
    }

    private static Xml.Tag updateProperties(final Xml.Tag properties, final String coverageModule) {
        final List<Content> content = new ArrayList<>();
        for (final var child : properties.getContent()) {
            if (child instanceof final Xml.Tag tag) {
                final var name = tag.getName();
                if (COVERAGE_MODULE.equals(name)) {
                    content.add(tag.withValue(coverageModule));
                    continue;
                }
                if (REMOVED_PROPERTIES.contains(name) || name.startsWith("sonar.")) {
                    dropComments(content);
                    continue;
                }
            }
            content.add(child);
        }
        return properties.withContent(content);
    }

    private static List<Xml.Tag> missingProperties(
            final Xml.Tag properties, final String coverageModule) {
        final List<Xml.Tag> added = new ArrayList<>();
        if (properties.getChild(COVERAGE_MODULE).isEmpty()) {
            added.add(Xml.Tag.build(property(COVERAGE_MODULE, coverageModule)));
        }
        if (properties.getChild(NULL_MARKING).isEmpty()) {
            added.add(Xml.Tag.build(property(NULL_MARKING, "WARN")));
        }
        return added;
    }

    /**
     * Append with the indentation of the last property. AddToTagVisitor would reformat around blank
     * lines and comments and indent the new properties like their parent.
     */
    private static Xml.Tag withAdded(final Xml.Tag properties, final List<Xml.Tag> added) {
        if (added.isEmpty()) {
            return properties;
        }
        final var children = properties.getChildren();
        final String indent;
        if (children.isEmpty()) {
            // <properties> is a child of <project>, so its own indentation is one level.
            final var own = properties.getPrefix();
            final var level = own.substring(own.lastIndexOf('\n') + 1);
            indent = "\n" + level + level;
        } else {
            final var prefix = children.getLast().getPrefix();
            indent = "\n" + prefix.substring(prefix.lastIndexOf('\n') + 1);
        }
        final List<Content> content = new ArrayList<>(properties.getContent());
        added.forEach(t -> content.add(t.withPrefix(indent)));
        return properties.withContent(content);
    }

    private static String property(final String name, final String value) {
        return "<" + name + ">" + value + "</" + name + ">";
    }

    /**
     * The parent manages every uetlib artifact through the self-bom. Remove plain pins and BOM
     * imports; keep customised entries such as exclusions or scopes without their version.
     */
    private static Xml.Tag withoutUetlibManagement(final Xml.Tag root) {
        final var management = root.getChild("dependencyManagement").orElse(null);
        final var dependencies =
                Optional.ofNullable(management)
                        .flatMap(m -> m.getChild("dependencies"))
                        .orElse(null);
        if (management == null || dependencies == null) {
            return root;
        }
        final List<Content> content = new ArrayList<>();
        for (final var child : dependencies.getContent()) {
            if (child instanceof final Xml.Tag tag && isPinnedUetlibDependency(tag)) {
                if (isPlain(tag)) {
                    dropComments(content);
                } else {
                    content.add(
                            tag.withContent(
                                    tag.getContent().stream()
                                            .filter(c ->
                                                    !(c instanceof Xml.Tag t)
                                                            || !"version".equals(t.getName()))
                                            .toList()));
                }
                continue;
            }
            content.add(child);
        }
        final var updated = dependencies.withContent(content);
        if (updated.getChildren().isEmpty() && management.getContent().size() == 1) {
            return root.withContent(
                    root.getContent().stream().filter(c -> c != management).toList());
        }
        return replace(root, management, replace(management, dependencies, updated));
    }

    /**
     * Remove redundant Spring Boot and Jib plugin management overrides and the JaCoCo plugin. Keep
     * project-specific configuration in managed plugins.
     */
    private static Xml.Tag withoutParentPlugins(final Xml.Tag root) {
        final var build = root.getChild("build").orElse(null);
        if (build == null) {
            return root;
        }
        var updated = build;
        final var management = build.getChild("pluginManagement").orElse(null);
        if (management != null) {
            final var plugins = management.getChild("plugins").orElse(null);
            if (plugins != null) {
                updated =
                        withoutEmpty(
                                updated,
                                management,
                                withoutEmpty(
                                        management, plugins, withoutManagedOverrides(plugins)));
            }
        }
        final var plugins = updated.getChild("plugins").orElse(null);
        if (plugins != null) {
            updated = withoutEmpty(updated, plugins, withoutPlugins(plugins, PARENT_PLUGINS));
        }
        if (updated == build) {
            return root;
        }
        return withoutEmpty(root, build, updated);
    }

    /**
     * Remove the inherited version, executions and mainClass from Spring Boot and Jib plugins. Keep
     * project-specific configuration and other plugin settings.
     */
    private static Xml.Tag withBarePlugins(final Xml.Tag root) {
        final var build = root.getChild("build").orElse(null);
        final var plugins =
                Optional.ofNullable(build).flatMap(b -> b.getChild("plugins")).orElse(null);
        if (build == null || plugins == null) {
            return root;
        }
        final var content =
                plugins.getContent().stream()
                        .map(c ->
                                c instanceof final Xml.Tag tag && isPlugin(tag, MANAGED_PLUGINS)
                                        ? bare(tag)
                                        : c)
                        .toList();
        // Tags are equal by id, so compare the instances.
        final var before = plugins.getContent();
        if (IntStream.range(0, content.size()).allMatch(i -> content.get(i) == before.get(i))) {
            return root;
        }
        return replace(root, build, replace(build, plugins, plugins.withContent(content)));
    }

    /**
     * The parent manages the version and the executions; the main class is detected. Other
     * configuration, such as excludes or Jib images, is project-specific and kept.
     */
    private static Xml.Tag bare(final Xml.Tag plugin) {
        var changed = false;
        final List<Content> content = new ArrayList<>();
        for (final var child : plugin.getContent()) {
            if (child instanceof final Xml.Tag tag) {
                if (Set.of("version", "executions").contains(tag.getName())) {
                    dropComments(content);
                    changed = true;
                    continue;
                }
                if ("configuration".equals(tag.getName())
                        && tag.getChild("mainClass").isPresent()) {
                    changed = true;
                    final var configuration =
                            tag.withContent(
                                    tag.getContent().stream()
                                            .filter(c ->
                                                    !(c instanceof final Xml.Tag t)
                                                            || !"mainClass".equals(t.getName()))
                                            .toList());
                    if (configuration.getChildren().isEmpty()) {
                        dropComments(content);
                    } else {
                        content.add(configuration);
                    }
                    continue;
                }
            }
            content.add(child);
        }
        return changed ? plugin.withContent(content) : plugin;
    }

    /**
     * The parent manages uetlib through the self-bom, so dependencies drop their versions instead
     * of being pinned to the migration releases. Dependencies of the old Datatrans and inotify
     * coordinates are migrated afterwards and stay versionless.
     */
    private static Xml.Tag withoutUetlibVersions(final Xml.Tag root) {
        final var dependencies = root.getChild("dependencies").orElse(null);
        if (dependencies == null) {
            return root;
        }
        var changed = false;
        final List<Content> content = new ArrayList<>();
        for (final var child : dependencies.getContent()) {
            if (child instanceof final Xml.Tag tag
                    && "dependency".equals(tag.getName())
                    && isManagedByParent(tag)
                    && tag.getChild("version").isPresent()) {
                changed = true;
                content.add(
                        tag.withContent(
                                tag.getContent().stream()
                                        .filter(c ->
                                                !(c instanceof final Xml.Tag t)
                                                        || !"version".equals(t.getName()))
                                        .toList()));
                continue;
            }
            content.add(child);
        }
        return changed ? replace(root, dependencies, dependencies.withContent(content)) : root;
    }

    private static boolean isManagedByParent(final Xml.Tag dependency) {
        final var group = dependency.getChildValue("groupId").orElse("");
        final var artifact = dependency.getChildValue("artifactId").orElse("");
        return (UETLIB_GROUPS.contains(group) && MANAGED_ARTIFACTS.contains(artifact))
                || ("ch.ubique".equals(group) && "inotify".equals(artifact))
                || ("ch.ubique.datatrans".equals(group)
                        && Set.of("datatrans-api-client-model", "datatrans-api-client-service")
                                .contains(artifact));
    }

    /** Remove the comments directly in front of a removed element. */
    private static void dropComments(final List<Content> content) {
        while (!content.isEmpty() && content.getLast() instanceof Xml.Comment) {
            content.removeLast();
        }
    }

    private static Xml.Tag withoutManagedOverrides(final Xml.Tag plugins) {
        final List<Content> content = new ArrayList<>();
        for (final var child : plugins.getContent()) {
            if (child instanceof final Xml.Tag tag && isPlugin(tag, MANAGED_PLUGINS)) {
                final var updated = bare(tag);
                if (updated.getChildren().stream()
                        .allMatch(t -> Set.of("groupId", "artifactId").contains(t.getName()))) {
                    dropComments(content);
                } else {
                    content.add(updated);
                }
            } else {
                content.add(child);
            }
        }
        return plugins.withContent(content);
    }

    private static Xml.Tag withoutPlugins(final Xml.Tag plugins, final Map<String, String> remove) {
        final List<Content> content = new ArrayList<>();
        for (final var child : plugins.getContent()) {
            if (child instanceof final Xml.Tag tag && isPlugin(tag, remove)) {
                dropComments(content);
                continue;
            }
            content.add(child);
        }
        return content.size() == plugins.getContent().size()
                ? plugins
                : plugins.withContent(content);
    }

    private static boolean isPlugin(final Xml.Tag tag, final Map<String, String> plugins) {
        final var group = plugins.get(tag.getChildValue("artifactId").orElse(""));
        return "plugin".equals(tag.getName())
                && group != null
                && group.equals(tag.getChildValue("groupId").orElse(group));
    }

    /** Replace a child that changed, dropping it once nothing is left in it. */
    private static Xml.Tag withoutEmpty(
            final Xml.Tag parent, final Xml.Tag before, final Xml.Tag after) {
        if (after == before) {
            return parent;
        }
        if (after.getChildren().isEmpty()) {
            return parent.withContent(
                    parent.getContent().stream().filter(c -> c != before).toList());
        }
        return replace(parent, before, after);
    }

    private static boolean isPinnedUetlibDependency(final Xml.Tag tag) {
        return "dependency".equals(tag.getName())
                && UETLIB_GROUPS.contains(tag.getChildValue("groupId").orElse(""))
                && tag.getChildValue("artifactId").orElse("").startsWith("uetlib-")
                && tag.getChild("version").isPresent();
    }

    private static boolean isPlain(final Xml.Tag tag) {
        if (!tag.getContent().stream().allMatch(Xml.Tag.class::isInstance)
                || !tag.getAttributes().isEmpty()) {
            return false;
        }
        final var names = tag.getChildren().stream().map(Xml.Tag::getName).toList();
        final var bomImport =
                "pom".equals(tag.getChildValue("type").orElse(""))
                        && "import".equals(tag.getChildValue("scope").orElse(""));
        return Set.of("groupId", "artifactId", "version").containsAll(names)
                || (bomImport
                        && Set.of("groupId", "artifactId", "version", "type", "scope")
                                .containsAll(names));
    }

    private static Xml.Tag replace(
            final Xml.Tag parent, final Xml.Tag before, final Xml.Tag after) {
        return parent.withContent(
                parent.getContent().stream().map(c -> c == before ? after : c).toList());
    }
}
