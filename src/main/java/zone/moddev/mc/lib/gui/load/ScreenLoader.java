package zone.moddev.mc.lib.gui.load;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.loading.DefinitionLoader.SourceRoot;
import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.layout.TextMeasure;

/**
 * Finds and loads every screen file: {@code mmdlib/screen/<path>.xml} in each mod, whose ID is
 * {@code <mod>:<path>}. Each file is read ({@link ScreenReader}) and its getters resolved ({@link
 * ScreenBinder}) at startup, so a broken screen fails the game's start with the file and line
 * instead of failing when a player opens it.
 */
public final class ScreenLoader {
  /** The folder under a mod's {@code mmdlib/} root that holds screen files. */
  public static final String SECTION = "screen";

  private ScreenLoader() {}

  /**
   * Loads every screen file under {@code sources}, sorted by ID.
   *
   * @throws ScreenException for an invalid file, naming it
   * @throws IllegalArgumentException if two files have the same ID
   */
  public static Map<ResourceLocation, ScreenBinder.Bound> load(List<SourceRoot> sources) {
    var screens = new TreeMap<ResourceLocation, ScreenBinder.Bound>();
    for (var source : sources) {
      var folder = source.root().resolve(SECTION);
      if (!Files.isDirectory(folder)) continue;
      try (var files = Files.walk(folder)) {
        for (var file :
            files.filter(Files::isRegularFile).filter(ScreenLoader::isXml).sorted().toList()) {
          var id = id(source.namespace(), folder, file);
          var bound = loadFile(id, file, source.classLoader());
          if (screens.putIfAbsent(id, bound) != null)
            throw new IllegalArgumentException("Duplicate screen " + id + ": " + file);
        }
      } catch (IOException ex) {
        throw new UncheckedIOException("Cannot read screens in " + folder, ex);
      }
    }
    return screens;
  }

  /**
   * Reads and binds one screen file.
   *
   * @throws ScreenException if it is invalid
   * @throws IOException if it cannot be read
   */
  public static ScreenBinder.Bound loadFile(ResourceLocation id, Path file, ClassLoader loader)
      throws IOException {
    try (var input = Files.newInputStream(file)) {
      var screen = ScreenReader.read(id, file.toString(), input);
      try {
        // Heights never depend on text, so whether the tabs fit can be checked without a font.
        ScreenLayout.of(screen, TextMeasure.NONE);
      } catch (IllegalArgumentException ex) {
        throw new ScreenException(file.toString(), 0, ex.getMessage(), ex);
      }
      return ScreenBinder.bind(screen, loader);
    }
  }

  private static boolean isXml(Path file) {
    return file.getFileName().toString().endsWith(".xml");
  }

  private static ResourceLocation id(String namespace, Path folder, Path file) {
    var relative = folder.relativize(file).toString().replace('\\', '/');
    return ResourceLocation.fromNamespaceAndPath(
        namespace, relative.substring(0, relative.length() - ".xml".length()));
  }
}
