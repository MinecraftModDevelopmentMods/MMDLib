package zone.moddev.mc.lib.machine.definition;

import java.util.*;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.gui.load.ScreenReader.Node;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Screen;

/** Registered fragment builders; templates retain ownership of the enclosing screen. */
public final class MachineScreenGenerators {
  @FunctionalInterface
  public interface Generator {
    List<Node> generate(MachineDefinition machine, Node placeholder);
  }

  private static final Map<ResourceLocation, Generator> GENERATORS = new LinkedHashMap<>();

  static {
    register(ResourceLocation.parse("mmdlib:processing"), MachineScreenGenerators::processing);
    register(ResourceLocation.parse("mmdlib:resource-groups"), MachineScreenGenerators::resources);
    register(
        ResourceLocation.parse("mmdlib:processors"),
        (machine, node) -> {
          options(node, Set.of());
          return List.of(progress(machine, node));
        });
  }

  private MachineScreenGenerators() {}

  public static synchronized void register(ResourceLocation id, Generator generator) {
    if (GENERATORS.putIfAbsent(id, Objects.requireNonNull(generator)) != null)
      throw new IllegalArgumentException("Duplicate fill generator " + id);
  }

  public static List<Node> generate(MachineDefinition machine, Node node) {
    if (machine == null)
      throw MachineDefinitions.error(node, "Machine fill generator requires a machine definition");
    var id = MachineDefinitions.resource(node, "generator");
    var generator = GENERATORS.get(id);
    if (generator == null) throw MachineDefinitions.error(node, "Unknown fill generator " + id);
    return List.copyOf(generator.generate(machine, node));
  }

  /** Checks references against the resolved machine before any block can be placed. */
  public static void validate(MachineDefinition machine, Screen screen) {
    for (var element : screen.elements()) {
      if (element instanceof Element.Slot slot
          && slot.kind() != Element.SlotKind.GHOST
          && (slot.index() < 0 || slot.index() >= machine.slots().size()))
        throw new zone.moddev.mc.lib.gui.load.ScreenException(
            screen.source(slot.common()),
            slot.common().line(),
            "Slot index outside machine storage: " + slot.index());
      if (element instanceof Element.Button button
          && button.processorControl()
          && button.processorKey().filter(key -> !key.equals(machine.processor())).isPresent())
        throw new zone.moddev.mc.lib.gui.load.ScreenException(
            screen.source(button.common()),
            button.common().line(),
            "Unknown processor-key: " + button.processorKey().orElseThrow());
    }
  }

  /** Validates and computes immutable highlight bindings once at definition load. */
  public static Map<String, List<String>> compileHighlights(
      MachineDefinition machine, Screen screen) {
    validate(machine, screen);
    var highlights = new LinkedHashMap<String, List<String>>();
    var slots = new HashMap<Integer, String>();
    for (var group : machine.groups()) {
      highlights.put(group.id(), new ArrayList<>());
      group.members().forEach(index -> slots.put(index, group.id()));
    }
    highlights.put(machine.power().id(), new ArrayList<>());
    for (var element : screen.elements()) {
      var group =
          element instanceof Element.Slot slot
              ? slots.get(slot.index())
              : element instanceof Element.EnergyGauge ? machine.power().id() : null;
      if (group != null) element.common().id().ifPresent(id -> highlights.get(group).add(id));
    }
    highlights.replaceAll((key, ids) -> List.copyOf(ids));
    return Map.copyOf(highlights);
  }

  private static void options(Node node, Set<String> allowed) {
    for (var key : node.attributes().keySet())
      if (!Set.of("name", "generator").contains(key) && !allowed.contains(key))
        throw MachineDefinitions.error(node, "Unknown generator option " + key);
  }

  private static Node progress(MachineDefinition machine, Node node) {
    return n(
        node,
        "progress",
        Map.of(
            "id",
            "generated_progress",
            "value",
            "#getProgress",
            "max",
            "#getTime",
            "jei-recipes",
            machine.recipeType().id().toString()),
        List.of());
  }

  private static List<Node> processing(MachineDefinition machine, Node node) {
    options(node, Set.of());
    var main = new ArrayList<Node>();
    main.add(spacer(node));
    main.addAll(groups(machine, node, IoMode.INPUT, 3));
    main.add(progress(machine, node));
    main.addAll(groups(machine, node, IoMode.OUTPUT, 3));
    main.add(spacer(node));
    var rows = new ArrayList<Node>();
    rows.add(spacer(node));
    rows.add(n(node, "hbox", Map.of("spacing", "6", "align", "center"), main));
    var storage = groups(machine, node, IoMode.BOTH, 3);
    if (!storage.isEmpty()) {
      storage.addFirst(spacer(node));
      storage.addLast(spacer(node));
      rows.add(n(node, "hbox", Map.of("spacing", "6"), storage));
    }
    rows.add(spacer(node));
    // The base template supplies the matching spacer before its energy gauge.
    return List.of(spacer(node), n(node, "vbox", Map.of("spacing", "4"), rows));
  }

  private static List<Node> resources(MachineDefinition machine, Node node) {
    options(node, Set.of("role", "columns"));
    String role = MachineDefinitions.required(node, "role");
    IoMode mode =
        switch (role) {
          case "input" -> IoMode.INPUT;
          case "output" -> IoMode.OUTPUT;
          case "storage" -> IoMode.BOTH;
          default -> throw MachineDefinitions.error(node, "Unknown group role " + role);
        };
    int columns;
    try {
      columns = Integer.parseInt(node.attributes().getOrDefault("columns", "3"));
    } catch (NumberFormatException ex) {
      throw MachineDefinitions.error(node, "columns must be 1..9");
    }
    if (columns < 1 || columns > 9) throw MachineDefinitions.error(node, "columns must be 1..9");
    return groups(machine, node, mode, columns);
  }

  private static List<Node> groups(MachineDefinition machine, Node node, IoMode mode, int columns) {
    var result = new ArrayList<Node>();
    for (var group : machine.groups())
      if (group.mode() == mode || (mode == IoMode.BOTH && group.mode() == IoMode.DISABLED)) {
        var rows = new ArrayList<Node>();
        rows.add(spacer(node));
        for (int start = 0; start < group.members().size(); start += columns) {
          var row = new ArrayList<Node>();
          for (int j = start; j < Math.min(start + columns, group.members().size()); j++) {
            int index = group.members().get(j);
            row.add(
                n(
                    node,
                    "slot",
                    Map.of("id", "slot_" + index, "index", Integer.toString(index)),
                    List.of()));
          }
          rows.add(n(node, "hbox", Map.of("spacing", "2"), row));
        }
        rows.add(spacer(node));
        result.add(n(node, "vbox", Map.of("spacing", "2"), rows));
      }
    return result;
  }

  private static Node n(
      Node origin, String name, Map<String, String> attributes, List<Node> children) {
    return new Node(name, attributes, List.copyOf(children), origin.source(), origin.line());
  }

  private static Node spacer(Node origin) {
    return n(origin, "spacer", Map.of(), List.of());
  }
}
