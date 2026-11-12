package ch.uetlib.rewrite;

import static java.util.Map.entry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.text.PlainTextParser;

/** Migrates known configuration namespaces separately from Java package ownership. */
public class MigrateConfigurationProperties extends Recipe {
    static final List<Map.Entry<String, String>> PREFIXES = loadPrefixes();
    private static final Pattern PROPERTY =
            Pattern.compile(
                    "(?<![\\w.])ch\\.(?:ubique\\.(?:backend|uetlib|datatrans)|uetlib)[\\w.-]*");
    private static final Pattern CLASS_SEGMENT = Pattern.compile("(?:^|\\.)[A-Z]");

    private static List<Map.Entry<String, String>> loadPrefixes() {
        final var properties = new Properties();
        try (final var input =
                MigrateConfigurationProperties.class.getResourceAsStream(
                        "/ch/uetlib/rewrite/property-migrations.properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing property migration map");
            }
            properties.load(input);
        } catch (final IOException exception) {
            throw new IllegalStateException("Cannot load property migration map", exception);
        }
        return properties.entrySet().stream()
                .map(entry -> entry((String) entry.getKey(), (String) entry.getValue()))
                .sorted(
                        Comparator.<Map.Entry<String, String>>comparingInt(
                                        entry -> entry.getKey().length())
                                .reversed()
                                .thenComparing(Map.Entry::getKey))
                .toList();
    }

    static String migrateText(final String text) {
        final var matcher = PROPERTY.matcher(text);
        final var result = new StringBuilder();
        while (matcher.find()) {
            final var value = matcher.group();
            var replacement = value;
            // Let the package recipes handle Java class names in strings.
            if (!CLASS_SEGMENT.matcher(value).find()) {
                for (final var entry : PREFIXES) {
                    final var prefix = entry.getKey();
                    if (value.equals(prefix) || value.startsWith(prefix + ".")) {
                        replacement = entry.getValue() + value.substring(prefix.length());
                        break;
                    }
                }
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        var migrated = result.toString();
        for (final var entry : PREFIXES) {
            final var oldEnv = entry.getKey().replace('.', '_').toUpperCase(Locale.ROOT);
            final var newEnv = entry.getValue().replace('.', '_').toUpperCase(Locale.ROOT);
            migrated = migrated.replaceAll("(?<![\\w])" + oldEnv + "(?=_|\\b)", newEnv);
        }
        return migrated;
    }

    @Override
    public String getDisplayName() {
        return "Migrate uetlib configuration properties";
    }

    @Override
    public String getDescription() {
        return "Rename known uetlib property prefixes, Java property references and placeholders to ch.uetlib, including nested YAML and environment variable names.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        final List<Recipe> recipes = new ArrayList<>();
        for (final var entry : PREFIXES) {
            recipes.add(
                    new org.openrewrite.properties.ChangePropertyKey(
                            Pattern.quote(entry.getKey()) + "(\\..*)?",
                            entry.getValue() + "$1",
                            false,
                            true));
            recipes.add(
                    new org.openrewrite.yaml.ChangePropertyKey(
                            entry.getKey(), entry.getValue(), false, null, null));
        }
        recipes.add(new PropertyReferences());
        return recipes;
    }

    public static class PropertyReferences extends Recipe {
        @Override
        public String getDisplayName() {
            return "Migrate uetlib property references";
        }

        @Override
        public String getDescription() {
            return "Update property names in Java literals and configuration resource values without changing class references.";
        }

        @Override
        public TreeVisitor<?, ExecutionContext> getVisitor() {
            return new TreeVisitor<>() {
                @Override
                public @Nullable Tree preVisit(final Tree tree, final ExecutionContext context) {
                    stopAfterPreVisit();
                    if (tree instanceof J.CompilationUnit) {
                        return new JavaIsoVisitor<ExecutionContext>() {
                            @Override
                            public J.Literal visitLiteral(
                                    final J.Literal literal, final ExecutionContext ctx) {
                                final var updated = super.visitLiteral(literal, ctx);
                                if (!(updated.getValue() instanceof final String value)) {
                                    return updated;
                                }
                                final var annotation =
                                        getCursor().firstEnclosing(J.Annotation.class);
                                if (annotation != null
                                        && Set.of(
                                                        "ComponentScan",
                                                        "EntityScan",
                                                        "EnableJpaRepositories",
                                                        "SpringBootApplication",
                                                        "EnableJobs",
                                                        "EnableStorages")
                                                .contains(annotation.getSimpleName())) {
                                    return updated;
                                }
                                final var replacement = migrateText(value);
                                if (replacement.equals(value)) {
                                    return updated;
                                }
                                final var source = updated.getValueSource();
                                return updated.withValue(replacement)
                                        .withValueSource(
                                                source == null ? null : migrateText(source));
                            }
                        }.visit(tree, context);
                    }
                    if (!(tree instanceof final SourceFile source)) {
                        return tree;
                    }
                    final var path = source.getSourcePath().toString();
                    if ("pom.xml".equals(path) || path.endsWith("/pom.xml")) {
                        return tree;
                    }
                    if (!path.matches(".*\\.(properties|ya?ml|xml|json)$")) {
                        return tree;
                    }
                    final var before = source.printAll();
                    final var after = migrateText(before);
                    return before.equals(after)
                            ? tree
                            : PlainTextParser.convert(source).withText(after);
                }
            };
        }
    }
}
