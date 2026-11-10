package zone.moddev.mc.lib.compat.CraftTweaker;

import java.io.IOException;
import java.io.UncheckedIOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Optional;

/**
 * The blank material templates: blockstates, models and grayscale textures
 * whose names contain {@link #TOKEN}. Each file is read from the first root
 * that has it, so a pack author can replace any template, for every script
 * material at once, by putting a file at the same path under
 * {@code scripts/mmdlibdata/templates/}. MMDLib's own templates are in its jar
 * under {@code
 * mmdlib_templates/}; {@code scripts/generate_material_templates.py} builds
 * them.
 */
final class MaterialTemplates {
    /**
     * Stands for the material's name (its ID path) in template paths and
     * contents.
     */
    static final String TOKEN = "__material__";

    private final List<Path> roots;

    /**
     * @param roots
     *            template roots, highest priority first; missing directories
     *            are skipped
     */
    MaterialTemplates(List<Path> roots) {
        this.roots = List.copyOf(roots);
    }

    /**
     * The template at {@code path}, relative to a template root, such as {@code
     * "textures/item/__material___ingot.png"}.
     */
    Optional<byte[]> read(String path) {
        for (var root : roots) {
            var file = root.resolve(path);
            if (Files.isRegularFile(file))
                try {
                    return Optional.of(Files.readAllBytes(file));
                } catch (IOException ex) {
                    throw new UncheckedIOException(
                        "Cannot read material template " + file, ex);
                }
        }
        return Optional.empty();
    }
}
