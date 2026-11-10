package zone.moddev.mc.lib.machine.definition;

import java.util.*;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.gui.layout.GroupHighlights;
import zone.moddev.mc.lib.gui.load.ResourceValidationContext;
import zone.moddev.mc.lib.gui.load.ScreenException;
import zone.moddev.mc.lib.gui.load.ScreenReader.Node;
import zone.moddev.mc.lib.gui.model.ElementGroup;
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
    register(ResourceLocation.parse("mmdlib:tanks"), MachineScreenGenerators::tanks);
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
    var inventories = new LinkedHashMap<String, Integer>();
    for (var name : machine.inventoryNames()) inventories.merge(name, 1, Integer::sum);
    var context =
        new ResourceValidationContext(
            screen,
            Map.of(
                "items",
                machine.slots().size(),
                "fluids",
                machine.fluids() == null ? 0 : machine.fluids().tanks().size(),
                "energy",
                machine.power() == null ? 0 : 1),
            inventories,
            Map.of(
                "processors",
                machine.processor() == null ? Set.of() : Set.of(machine.processor())));
    for (var element : screen.elements()) {
      try {
        element.validateResources(context);
      } catch (IllegalArgumentException failure) {
        throw new ScreenException(
            screen.source(element.common()), element.common().line(), failure.getMessage());
      }
    }
  }

  /** Validates and computes immutable highlight bindings once at definition load. */
  public static Map<String, List<String>> compileHighlights(
      MachineDefinition machine, Screen screen) {
    validate(machine, screen);
    var groups = new ArrayList<ElementGroup>();
    for (var group : machine.groups()) {
      var local =
          java.util.stream.IntStream.range(0, machine.inventorySlots(group.id()).size())
              .boxed()
              .toList();
      groups.add(
          new ElementGroup(
              group.id(),
              List.of(),
              java.util.Optional.of(
                  new ElementGroup.Members(
                      ResourceType.ITEMS.id(), group.id(), local, group.members()))));
    }
    if (machine.power() != null)
      groups.add(
          new ElementGroup(
              machine.power().id(),
              List.of(),
              java.util.Optional.of(
                  new ElementGroup.Members(
                      ResourceType.ENERGY.id(), machine.power().id(), List.of(0)))));
    if (machine.fluids() != null)
      for (var group : machine.fluids().groups())
        groups.add(
            new ElementGroup(
                group.id(),
                List.of(),
                java.util.Optional.of(
                    new ElementGroup.Members(
                        ResourceType.FLUIDS.id(), machine.fluids().id(), group.members()))));
    var result = new LinkedHashMap<String, List<String>>();
    GroupHighlights.of(screen, groups, element -> element.common().id().isPresent())
        .forEach(
            (id, targets) ->
                result.put(
                    id,
                    targets.stream().map(element -> element.common().id().orElseThrow()).toList()));
    return Collections.unmodifiableMap(result);
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

  private static List<Node> tanks(MachineDefinition machine, Node node) {
    options(node, Set.of());
    var result = new ArrayList<Node>();
    if (machine.fluids() == null) return result;
    for (int index = 0; index < machine.fluids().tanks().size(); index++)
      result.add(
          n(
              node,
              "fluid-tank",
              Map.of("id", "tank_" + index, "length", "3s", "tank", Integer.toString(index)),
              List.of()));
    return result;
  }

  private static List<Node> groups(MachineDefinition machine, Node node, IoMode mode, int columns) {
    var result = new ArrayList<Node>();
    for (var group : machine.groups())
      if (group.mode() == mode || (mode == IoMode.BOTH && group.mode() == IoMode.DISABLED)) {
        var rows = new ArrayList<Node>();
        rows.add(spacer(node));
        var children = new ArrayList<Node>();
        var members = machine.inventorySlots(group.id());
        for (int index : group.members())
          children.add(
              n(
                  node,
                  "item-slot",
                  Map.of("id", "slot_" + index, "index", Integer.toString(members.indexOf(index))),
                  List.of()));
        rows.add(
            n(
                node,
                "coloured-group",
                Map.of(
                    "id",
                    "inventory_" + group.id(),
                    "color",
                    String.format("#%06X", group.color() & 0xffffff)),
                List.of(
                    n(
                        node,
                        "inventory",
                        Map.of(
                            "storage",
                            group.id(),
                            "columns",
                            Integer.toString(columns),
                            "spacing",
                            "2"),
                        children))));
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
