package ch.ubique.uetlib.rewrite;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.openrewrite.Recipe;
import org.openrewrite.java.ChangeType;

/** Relocates only known library types; shared package namespaces must not be moved wholesale. */
public class MigrateOwnedTypes extends Recipe {
    static final Map<String, String> TYPES = loadTypes();

    private static Map<String, String> loadTypes() {
        Properties properties = new Properties();
        try (InputStream input =
                MigrateOwnedTypes.class.getResourceAsStream(
                        "/ch/ubique/uetlib/rewrite/type-migrations.properties")) {
            if (input == null) throw new IllegalStateException("Missing uetlib type migrations");
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load uetlib type migrations", exception);
        }
        Map<String, String> result = new TreeMap<>();
        properties.forEach((key, value) -> result.put((String) key, (String) value));
        return result;
    }

    @Override
    public String getDisplayName() {
        return "Align uetlib types with their owning modules";
    }

    @Override
    public String getDescription() {
        return "Move explicitly listed types to module-specific namespaces without relocating unrelated core, test or WebP types.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return TYPES.entrySet().stream()
                .sorted(Comparator.comparing(entry -> !entry.getKey().contains("$")))
                .map(
                        entry ->
                                (Recipe)
                                        new ChangeType(
                                                entry.getKey(),
                                                entry.getValue(),
                                                entry.getKey().contains("$")))
                .toList();
    }
}
