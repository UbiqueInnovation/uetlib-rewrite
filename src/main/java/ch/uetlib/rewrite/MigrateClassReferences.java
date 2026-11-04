package ch.uetlib.rewrite;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

/** Moves class-name strings and package-scanning annotations without renaming property keys. */
public class MigrateClassReferences extends Recipe {
    static final String OLD = "ch.ubique.backend";
    static final String NEW = "ch.uetlib";
    static final String CLIENT_OLD = "ch.ubique.datatrans";
    static final String CLIENT_NEW = "ch.uetlib.payment.datatrans.client";

    // Only unambiguous package roots can be mapped in scanning/logging strings.
    private static final Map<String, String> OWNED_PACKAGES =
            Map.of(
                    "ch.uetlib.security", "ch.uetlib.spring.boot.security",
                    "ch.uetlib.cloud", "ch.uetlib.spring.cloud",
                    "ch.uetlib.spring.validation", "ch.uetlib.spring.boot.validation",
                    "ch.uetlib.test.enabledif", "ch.uetlib.spring.boot.test.enabledif",
                    "ch.uetlib.image.blurhash", "ch.uetlib.core.image.blurhash",
                    "ch.uetlib.image.util", "ch.uetlib.core.image.util",
                    "ch.uetlib.geodb", "ch.uetlib.geo.db",
                    "ch.uetlib.geojob", "ch.uetlib.geo.job",
                    "ch.uetlib.storage.spring", "ch.uetlib.storage.spring.boot",
                    "ch.uetlib.image.webp", "ch.uetlib.webp");

    static String packages(String text) {
        String migrated =
                text.replace("ch.ubique.uetlib", NEW)
                        .replace(OLD, NEW)
                        .replace(CLIENT_OLD, CLIENT_NEW);
        Matcher matcher = REFERENCE.matcher(migrated);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String reference = matcher.group();
            String replacement = reference;
            for (var entry : MigrateOwnedTypes.TYPES.entrySet()) {
                String type = entry.getKey();
                if (reference.equals(type)
                        || reference.startsWith(type + ".")
                        || reference.startsWith(type + "$")) {
                    replacement = entry.getValue() + reference.substring(type.length());
                    break;
                }
            }
            // Class mappings run first: the old WebP namespace also held core image helpers.
            for (var entry : OWNED_PACKAGES.entrySet()) {
                String oldPackage = entry.getKey();
                String newPackage = entry.getValue();
                if (replacement.equals(newPackage) || replacement.startsWith(newPackage + "."))
                    continue;
                if (replacement.equals(oldPackage) || replacement.startsWith(oldPackage + ".")) {
                    replacement = newPackage + replacement.substring(oldPackage.length());
                    break;
                }
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static final Pattern REFERENCE =
            Pattern.compile(
                    "\\bch\\.(?:ubique\\.(?:backend|datatrans|uetlib)|uetlib)(?=\\.)[\\w.$]*");
    private static final Set<String> SCANNING =
            Set.of(
                    "ComponentScan",
                    "EntityScan",
                    "EnableJpaRepositories",
                    "SpringBootApplication",
                    "EnableJobs",
                    "EnableStorages");

    static String classNames(String text) {
        Matcher matcher = REFERENCE.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = matcher.group();
            String replacement =
                    value.chars().anyMatch(Character::isUpperCase) ? packages(value) : value;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    @Override
    public String getDisplayName() {
        return "Migrate uetlib class-name strings";
    }

    @Override
    public String getDescription() {
        return "Update fully qualified class-name strings and package-scanning annotations while leaving existing Spring configuration keys unchanged.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(
                    J.ClassDeclaration declaration, ExecutionContext context) {
                J.ClassDeclaration updated = super.visitClassDeclaration(declaration, context);
                // Preserve declaration kinds when ChangePackage rebuilds type attribution.
                if (updated.getType() instanceof JavaType.Class type) {
                    JavaType.FullyQualified.Kind kind =
                            JavaType.FullyQualified.Kind.valueOf(updated.getKind().name());
                    if (type.getKind() != kind) {
                        updated = updated.withType(type.withKind(kind));
                    }
                }
                return updated;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext context) {
                J.Literal updated = super.visitLiteral(literal, context);
                if (!(updated.getValue() instanceof String text)
                        || !(text.contains(OLD)
                                || text.contains(CLIENT_OLD)
                                || text.contains(NEW)
                                || text.contains("ch.ubique.uetlib"))) {
                    return updated;
                }
                J.Annotation annotation = getCursor().firstEnclosing(J.Annotation.class);
                J.NewClass constructor = getCursor().firstEnclosing(J.NewClass.class);
                boolean scanning =
                        annotation != null && SCANNING.contains(annotation.getSimpleName())
                                || constructor != null
                                        && constructor
                                                .getClazz()
                                                .printTrimmed()
                                                .endsWith("Reflections");
                J.VariableDeclarations.NamedVariable variable =
                        getCursor().firstEnclosing(J.VariableDeclarations.NamedVariable.class);
                J.MethodInvocation method = getCursor().firstEnclosing(J.MethodInvocation.class);
                boolean logger =
                        variable != null && variable.getSimpleName().equals("LOGGER_NAME")
                                || method != null
                                        && method.getSimpleName().equals("getLogger")
                                        && method.getSelect() != null
                                        && method.getSelect()
                                                .printTrimmed()
                                                .endsWith("LoggerFactory");
                scanning = scanning || logger;
                String replacement = scanning ? packages(text) : classNames(text);
                if (text.equals(replacement)) {
                    return updated;
                }
                String source = updated.getValueSource();
                return updated.withValue(replacement)
                        .withValueSource(
                                source == null
                                        ? null
                                        : scanning ? packages(source) : classNames(source));
            }
        };
    }
}
