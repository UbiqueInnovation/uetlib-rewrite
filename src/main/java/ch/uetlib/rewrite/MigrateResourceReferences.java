package ch.uetlib.rewrite;

import java.nio.file.Path;
import java.util.regex.Pattern;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainTextParser;

/** Resource migration runs last because converting a file to text discards its language tree. */
@SuppressWarnings("unused") // linked in uetlib-migration.yml
public class MigrateResourceReferences extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate uetlib resource references";
    }

    @Override
    public String getDescription() {
        return "Update service registrations, Spring imports, logging packages, protobuf Java packages and qualified class names in configuration resources.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<>() {
            @Override
            public Tree preVisit(final Tree tree, final ExecutionContext context) {
                stopAfterPreVisit();
                if (!(tree instanceof final SourceFile source)) {
                    return tree;
                }
                final var path = "/" + source.getSourcePath().toString().replace('\\', '/');
                final var text = source.printAll();
                final String replacement;
                var newPath = source.getSourcePath().toString();
                if (path.contains("/META-INF/services/")
                        || path.contains("/META-INF/spring/") && path.endsWith(".imports")) {
                    replacement = MigrateClassReferences.packages(text);
                    newPath = MigrateClassReferences.packages(newPath);
                } else if (path.endsWith(".proto")) {
                    replacement =
                            Pattern.compile("(java_package\\s*=\\s*\"\\s*)([\\w.]+)")
                                    .matcher(text)
                                    .replaceAll(match ->
                                            match.group(1)
                                                    + MigrateClassReferences.packages(
                                                            match.group(2)));
                } else if (path.matches(".*/logback[^/]*\\.xml")) {
                    replacement =
                            Pattern.compile("(<logger\\b[^>]*\\bname\\s*=\\s*[\"'])([\\w.]+)")
                                    .matcher(MigrateClassReferences.classNames(text))
                                    .replaceAll(match ->
                                            match.group(1)
                                                    + MigrateClassReferences.packages(
                                                            match.group(2)));
                } else if (path.endsWith(".xml")
                        || path.endsWith(".properties")
                        || path.endsWith(".yml")
                        || path.endsWith(".yaml")) {
                    replacement = MigrateClassReferences.classNames(text);
                } else {
                    return tree;
                }
                if (replacement.equals(text) && newPath.equals(source.getSourcePath().toString())) {
                    return tree;
                }
                return PlainTextParser.convert(source)
                        .withText(replacement)
                        .withSourcePath(Path.of(newPath));
            }
        };
    }
}
