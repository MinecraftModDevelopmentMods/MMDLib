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
import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Screen;

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
    return load(sources, Map.of(), ScreenFillGenerator.UNAVAILABLE);
  }

  /** Loads embedded machine screens through the same composition and validation pipeline. */
  public static Map<ResourceLocation, ScreenBinder.Bound> load(
      List<SourceRoot> sources,
      Map<ResourceLocation, ScreenReader.Node> embedded,
      ScreenFillGenerator generator) {
    WidgetTypes.GLOBAL.freeze();
    zone.moddev.mc.lib.gui.value.ValueTypeRegistry.GLOBAL.freeze();
    ScreenTags.GLOBAL.freeze();
    var discovered = discover(sources);
    var screens = new TreeMap<ResourceLocation, ScreenBinder.Bound>();
    embedded.forEach(
        (id, node) -> {
          if (discovered.nodes().putIfAbsent(id, node) != null)
            throw new IllegalArgumentException("Duplicate screen " + id + ": " + node.source());
          discovered.loaders().put(id, ScreenLoader.class.getClassLoader());
        });
    read(discovered.nodes(), WidgetTypes.GLOBAL, generator)
        .forEach((id, screen) -> screens.put(id, bind(screen, discovered.loaders().get(id))));
    return screens;
  }

  /** Reads and composes concrete screens without loading host classes, for layout tools. */
  public static Map<ResourceLocation, Screen> read(List<SourceRoot> sources, WidgetTypes widgets) {
    return read(sources, widgets, ScreenTags.GLOBAL);
  }

  public static Map<ResourceLocation, Screen> read(
      List<SourceRoot> sources, WidgetTypes widgets, ScreenTags tags) {
    return read(discover(sources, tags).nodes(), widgets, ScreenFillGenerator.UNAVAILABLE, tags);
  }

  private record Definitions(
      Map<ResourceLocation, ScreenReader.Node> nodes, Map<ResourceLocation, ClassLoader> loaders) {}

  private static Definitions discover(List<SourceRoot> sources) {
    return discover(sources, ScreenTags.GLOBAL);
  }

  private static Definitions discover(List<SourceRoot> sources, ScreenTags tags) {
    var definitions = new TreeMap<ResourceLocation, ScreenReader.Node>();
    var loaders = new TreeMap<ResourceLocation, ClassLoader>();
    for (var source : sources) {
      var folder = source.root().resolve(SECTION);
      if (!Files.isDirectory(folder)) continue;
      try (var files = Files.walk(folder)) {
        for (var file :
            files.filter(Files::isRegularFile).filter(ScreenLoader::isXml).sorted().toList()) {
          var id = id(source.namespace(), folder, file);
          try (var input = Files.newInputStream(file)) {
            var definition = ScreenReader.parse(file.toString(), input, tags);
            if (definitions.putIfAbsent(id, definition) != null)
              throw new IllegalArgumentException("Duplicate screen " + id + ": " + file);
            loaders.put(id, source.classLoader());
          }
        }
      } catch (IOException ex) {
        throw new UncheckedIOException("Cannot read screens in " + folder, ex);
      }
    }
    return new Definitions(definitions, loaders);
  }

  private static Map<ResourceLocation, Screen> read(
      Map<ResourceLocation, ScreenReader.Node> definitions, WidgetTypes widgets) {
    return read(definitions, widgets, ScreenFillGenerator.UNAVAILABLE);
  }

  /** Compiles definition trees, useful for generators and definition validation tools. */
  public static Map<ResourceLocation, Screen> read(
      Map<ResourceLocation, ScreenReader.Node> definitions,
      WidgetTypes widgets,
      ScreenFillGenerator generator) {
    return read(definitions, widgets, generator, ScreenTags.GLOBAL);
  }

  /** Compiles using an explicit registry, including embedded and generated namespaced nodes. */
  public static Map<ResourceLocation, Screen> read(
      Map<ResourceLocation, ScreenReader.Node> definitions,
      WidgetTypes widgets,
      ScreenFillGenerator generator,
      ScreenTags tags) {
    var validated = new TreeMap<ResourceLocation, ScreenReader.Node>();
    definitions.forEach((id, node) -> validated.put(id, ScreenReader.validateExpanded(node, tags)));
    definitions = validated;
    var screens = new TreeMap<ResourceLocation, Screen>();
    var templates = new ScreenTemplates(definitions, generator, tags);
    // Discover all definitions first: bases may be in later files or another mod.
    for (var id : definitions.keySet()) templates.resolve(id);
    for (var id : definitions.keySet()) {
      if (ScreenTemplates.flag(definitions.get(id), "abstract")) continue;
      screens.put(id, ScreenReader.build(id, templates.expand(id), widgets, tags));
    }
    return screens;
  }

  /**
   * Reads and binds one standalone screen file. Use {@link #load} to resolve bases across files.
   *
   * @throws ScreenException if it is invalid
   * @throws IOException if it cannot be read
   */
  public static ScreenBinder.Bound loadFile(ResourceLocation id, Path file, ClassLoader loader)
      throws IOException {
    try (var input = Files.newInputStream(file)) {
      var screen = ScreenReader.read(id, file.toString(), input);
      return bind(screen, loader);
    }
  }

  private static ScreenBinder.Bound bind(Screen screen, ClassLoader loader) {
    try {
      // Heights never depend on text, so whether the tabs fit can be checked without a font.
      ScreenLayout.of(screen, TextMeasure.NONE);
    } catch (IllegalArgumentException ex) {
      throw new ScreenException(screen.source(), 0, ex.getMessage(), ex);
    }
    return ScreenBinder.bind(screen, loader);
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
