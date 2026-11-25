package zone.moddev.mc.lib.machine.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.gui.layout.GroupHighlights;
import zone.moddev.mc.lib.gui.load.ResourceValidationContext;
import zone.moddev.mc.lib.gui.load.ScreenException;
import zone.moddev.mc.lib.gui.load.ScreenFillGenerator;
import zone.moddev.mc.lib.gui.load.ScreenReader.Node;
import zone.moddev.mc.lib.gui.load.ScreenTags;
import zone.moddev.mc.lib.gui.model.ElementGroup;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.machine.feature.Processor;

/**
 * Registered fragment builders; templates retain ownership of the enclosing
 * screen.
 */
public final class MachineScreenGenerators {
    @FunctionalInterface
    public interface Generator {
        List<Node> generate(MachineDefinition machine, Node placeholder);
    }

    private static final Map<ResourceLocation, Generator> GENERATORS = new LinkedHashMap<>();

    static {
        register(ResourceLocation.parse("mmdlib:component-upgrades"),
            (machine, node) -> {
                options(node, Set.of());
                if (machine.behavior().gearboxSlot() == null
                    && machine.behavior().capacitorSlot() == null)
                    return List.of();
                return List.of(n(node, "upgrades-drawer",
                    Map.of("id", "upgrades"),
                    List.of(n(node, "machine-upgrades", Map.of(), List.of()))));
            });
        register(ResourceLocation.parse("mmdlib:processing"),
            MachineScreenGenerators::processing);
        register(ResourceLocation.parse("mmdlib:resource-groups"),
            MachineScreenGenerators::resources);
        register(ResourceLocation.parse("mmdlib:tanks"),
            MachineScreenGenerators::tanks);
        register(ResourceLocation.parse("mmdlib:processors"),
            (machine, node) -> {
                options(node, Set.of());
                return machine.processors().stream()
                    .filter(config -> config.recipeType() != null)
                    .map(config -> progress(machine, config, node)).toList();
            });
    }

    private MachineScreenGenerators() {
    }

    /** An ordered layout owns the base screen's entire machine row. */
    static Node withLayout(MachineDefinition machine, Node screen,
        Node layout) {
        var claimed = upgradeSlots(machine, screen);
        var controls = layout.children().stream()
            .map(entry -> switch (entry.name()) {
                case "tank" -> layoutTank(machine, entry);
                case "inventory" -> layoutInventory(machine, entry, claimed);
                case "processor" -> {
                    String ref = MachineDefinitions.required(entry, "ref");
                    var processor = machine.processors().stream()
                        .filter(config -> config.id().equals(ref)).findFirst()
                        .orElseThrow(() -> MachineDefinitions.error(entry,
                            "Unknown layout processor " + ref));
                    yield progress(machine, processor, entry);
                }
                case "energy" -> {
                    if (machine.power() == null)
                        throw MachineDefinitions.error(entry,
                            "A layout energy gauge requires energy storage");
                    yield n(entry, "energy-gauge",
                        Map.of("id", "energy", "length", "3s"), List.of());
                }
                default -> throw MachineDefinitions.error(entry,
                    "Unexpected layout entry " + entry.name());
            }).toList();
        var fills = new ArrayList<Node>();
        for (var child : screen.children())
            if (!child.name().equals("fill")
                || !Set.of("machine", "tanks", "energy")
                    .contains(child.attributes().getOrDefault("name", "")))
                fills.add(child);
        var row = n(layout, "hbox", Map.of("spacing", "6", "align", "center"),
            controls);
        fills.add(n(layout, "fill", Map.of("name", "machine"),
            List.of(spacer(layout), row)));
        fills.add(n(layout, "fill", Map.of("name", "tanks"), List.of()));
        fills.add(n(layout, "fill", Map.of("name", "energy"), List.of()));
        return n(screen, "screen", screen.attributes(), fills);
    }

    private static Node layoutTank(MachineDefinition machine, Node entry) {
        String ref = MachineDefinitions.required(entry, "ref");
        if (machine.fluids() != null) {
            var tanks = machine.fluids().tanks();
            for (int i = 0; i < tanks.size(); i++)
                if (tanks.get(i).id().equals(ref))
                    return tank(entry, i);
        }
        throw MachineDefinitions.error(entry, "Unknown layout tank " + ref);
    }

    private static Node layoutInventory(MachineDefinition machine, Node entry,
        Set<Integer> claimed) {
        String ref = MachineDefinitions.required(entry, "ref");
        int columns = Integer
            .parseInt(entry.attributes().getOrDefault("columns", "3"));
        for (var ghost : machine.ghosts())
            if (ghost.id().equals(ref))
                return ghostInventory(machine, entry, ghost, columns);
        var members = machine.inventorySlots(ref);
        var visible = members.stream().filter(index -> !claimed.contains(index))
            .toList();
        if (visible.isEmpty())
            throw MachineDefinitions.error(entry,
                "Layout inventory has no available slots: " + ref);
        var group = machine.groups().stream()
            .filter(config -> config.id().equals(ref)).findFirst().orElse(null);
        return itemInventory(entry, ref, group, columns, members, visible);
    }

    /**
     * Machine context for generated fills and template-owned literal defaults.
     */
    public static ScreenFillGenerator forMachines(
        Map<ResourceLocation, MachineDefinition> machines) {
        return forMachines(machines, null);
    }

    private static ScreenFillGenerator forMachines(
        Map<ResourceLocation, MachineDefinition> machines, Node composed) {
        return new ScreenFillGenerator() {
            @Override
            public ScreenFillGenerator forScreen(ResourceLocation id,
                Node root) {
                return forMachines(machines, root);
            }

            @Override
            public List<Node> generate(ResourceLocation id, Node node) {
                var machine = machines.get(id);
                if (machine != null && composed != null) {
                    var generator = MachineDefinitions.resource(node,
                        "generator");
                    if (generator.equals(
                        ResourceLocation.parse("mmdlib:component-upgrades"))
                        && declaresUpgradeLayout(composed))
                        return List.of();
                    var claimed = upgradeSlots(machine, composed);
                    if (generator
                        .equals(ResourceLocation.parse("mmdlib:processing")))
                        return processing(machine, node, claimed);
                    if (generator.equals(
                        ResourceLocation.parse("mmdlib:resource-groups")))
                        return resources(machine, node, claimed);
                }
                return MachineScreenGenerators.generate(machine, node);
            }

            @Override
            public Node prepareElement(ResourceLocation id, Node node) {
                var machine = machines.get(id);
                if (machine != null && machine.processors().size() > 1
                    && node.tag().equals(ScreenTags.name("inventory-controls"))
                    && node.children().size() == 1
                    && node.children().getFirst().name()
                        .equals("processor-button")
                    && node.children().getFirst().attributes()
                        .equals(Map.of("id", "toggle_0"))) {
                    var buttons = machine.processors().stream()
                        .map(config -> n(node, "processor-button",
                            Map.of("id", "toggle_" + config.id(),
                                "processor-key", config.id()),
                            List.of()))
                        .toList();
                    return new Node(node.name(), node.attributes(), buttons,
                        node.source(), node.line(), node.namespace());
                }
                if (machine != null
                    && node.tag().equals(ScreenTags.name("machine-upgrades")))
                    return upgradeSockets(machine, node);
                if (machine == null
                    || !node.tag().equals(ScreenTags.name("inventory"))
                    || !node.children().isEmpty()
                    || node.attributes().containsKey("count"))
                    return node;
                String storage = node.attributes().get("storage");
                int count = machine.inventorySlots(storage).size();
                if (count == 0)
                    throw MachineDefinitions.error(node,
                        "Automatic inventory requires a non-empty item storage: "
                            + storage);
                var attributes = new LinkedHashMap<>(node.attributes());
                attributes.put("count", Integer.toString(count));
                return new Node(node.name(), attributes, node.children(),
                    node.source(), node.line(), node.namespace());
            }

            @Override
            public boolean includeDefault(ResourceLocation id, Node node) {
                var filter = MachineDefinitions.resource(node,
                    "default-filter");
                var machine = machines.get(id);
                if (!filter.equals(
                    ResourceLocation.parse("mmdlib:processor-controls")))
                    throw MachineDefinitions.error(node,
                        "Unknown machine default filter " + filter);
                return machine == null || machine.hasProcessor();
            }
        };
    }

    /**
     * An authored upgrade layout replaces the inherited components-only drawer.
     */
    private static boolean declaresUpgradeLayout(Node node) {
        return node.tag().equals(ScreenTags.name("upgrades-drawer"))
            || node.tag().equals(ScreenTags.name("machine-upgrades"))
            || node.children().stream()
                .anyMatch(MachineScreenGenerators::declaresUpgradeLayout);
    }

    public static synchronized void register(ResourceLocation id,
        Generator generator) {
        if (GENERATORS.putIfAbsent(id,
            Objects.requireNonNull(generator)) != null)
            throw new IllegalArgumentException(
                "Duplicate fill generator " + id);
    }

    /**
     * Compose independent library sockets and the machine's own upgrade
     * inventory.
     */
    private static Node upgradeSockets(MachineDefinition machine, Node node) {
        if (!node.children().isEmpty())
            throw MachineDefinitions.error(node,
                "machine-upgrades is a shorthand without children; use ordinary inventories for an explicit layout");
        String storage = node.attributes().get("storage");
        var members = storage == null
            ? List.<Integer>of()
            : machine.inventorySlots(storage);
        if (storage != null && members.isEmpty())
            throw MachineDefinitions.error(node,
                "Unknown upgrade storage: " + storage);
        var behavior = machine.behavior();
        var components = new ArrayList<Node>();
        var componentIndexes = new java.util.HashSet<Integer>();
        String prefix = node.attributes().containsKey("id")
            ? node.attributes().get("id") + "_"
            : "";
        for (String kind : List.of("gearbox", "capacitor")) {
            String address = kind.equals("gearbox")
                ? behavior.gearboxSlot()
                : behavior.capacitorSlot();
            if (address == null)
                continue;
            int index = java.util.stream.IntStream
                .range(0, machine.slots().size())
                .filter(i -> machine.slots().get(i).id().equals(address))
                .findFirst().orElseThrow();
            componentIndexes.add(index);
            String inventory = machine.inventoryNames().get(index);
            int local = machine.inventorySlots(inventory).indexOf(index);
            var slot = upgradeSocket(machine, node, index, local, prefix + kind,
                kind);
            components.add(n(node, "inventory",
                Map.of("storage", inventory, "columns", "1", "spacing", "0"),
                List.of(slot)));
        }
        var extras = members.stream()
            .filter(index -> !componentIndexes.contains(index)).toList();
        if (extras.size() > 9)
            throw MachineDefinitions.error(node,
                "Machine upgrades supports at most nine extra sockets");
        var rows = new ArrayList<Node>();
        if (!components.isEmpty())
            rows.add(n(node, "vbox",
                Map.of("padding", "0", "spacing", "0", "align", "center"),
                List.of(n(node, "hbox",
                    Map.of("padding", "0", "spacing", "6", "align", "center"),
                    components))));
        if (!extras.isEmpty()) {
            var slots = new ArrayList<Node>();
            for (int index : extras) {
                int local = members.indexOf(index);
                slots.add(upgradeSocket(machine, node, index, local,
                    prefix + storage + "_" + local, "range"));
            }
            rows.add(n(node, "inventory",
                Map.of("storage", storage, "columns", "3", "spacing", "2"),
                slots));
        }
        if (rows.isEmpty())
            throw MachineDefinitions.error(node,
                "Machine has no upgrade sockets");
        var attributes = new LinkedHashMap<>(node.attributes());
        attributes.remove("storage");
        attributes.put("padding", "0");
        attributes.put("spacing", "8");
        attributes.put("align", "center");
        return n(node, "vbox", attributes, rows);
    }

    private static Node upgradeSocket(MachineDefinition machine, Node node,
        int index, int local, String id, String kind) {
        var slot = machine.slots().get(index);
        if (slot.role() != zone.moddev.mc.lib.api.capabilities.Role.MANUAL
            || (kind.equals("range") && slot.itemTag() == null
                && !machine.behavior().rangeSlots().contains(slot.id())))
            throw MachineDefinitions.error(node,
                "Upgrade socket must be manual and declare an upgrade binding or item-tag: "
                    + slot.id());
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("id", id);
        attributes.put("index", Integer.toString(local));
        attributes.put("item-tag",
            slot.itemTag() != null
                ? slot.itemTag().toString()
                : "mmdlib:" + kind + "_upgrades");
        attributes.put("kind", "upgrade");
        attributes.put("max-stack-size", kind.equals("range") ? "1" : "8");
        attributes.put("size", "normal");
        attributes.put("item-size", "16");
        if (!kind.equals("range")) {
            attributes.put("icon", "mmdlib:socket_" + kind);
            attributes.put("tooltip", "screen.mmdlib.upgrades." + kind);
        }
        return n(node, "upgrade-slot", attributes, List.of());
    }

    public static List<Node> generate(MachineDefinition machine, Node node) {
        if (machine == null)
            throw MachineDefinitions.error(node,
                "Machine fill generator requires a machine definition");
        var id = MachineDefinitions.resource(node, "generator");
        var generator = GENERATORS.get(id);
        if (generator == null)
            throw MachineDefinitions.error(node,
                "Unknown fill generator " + id);
        return List.copyOf(generator.generate(machine, node));
    }

    /**
     * Checks references against the resolved machine before any block can be
     * placed.
     */
    public static void validate(MachineDefinition machine, Screen screen) {
        var inventories = new LinkedHashMap<String, Integer>();
        for (var name : machine.inventoryNames())
            inventories.merge(name, 1, Integer::sum);
        machine.ghosts()
            .forEach(ghost -> inventories.put(ghost.id(), ghost.count()));
        var context = new ResourceValidationContext(screen,
            Map.of("items", machine.slots().size(), "fluids",
                machine.fluids() == null ? 0 : machine.fluids().tanks().size(),
                "energy", machine.power() == null ? 0 : 1),
            inventories,
            Map.of("processors",
                !machine.hasProcessor()
                    ? Set.of()
                    : machine.processors().stream()
                        .map(ProcessorConfiguration::id)
                        .collect(java.util.stream.Collectors.toSet())));
        Map<String, Processor<?>> declared = machine.processorSchemas();
        var defaults = machine.visualDefaults(
            machine.hasProcessor() ? declared.get(machine.processor()) : null);
        for (var element : screen.elements()) {
            try {
                element.validateResources(context);
                element.validateDefinition(declared, defaults);
            } catch (IllegalArgumentException failure) {
                throw new ScreenException(screen.source(element.common()),
                    element.common().line(), failure.getMessage());
            }
        }
    }

    /**
     * Validates and computes immutable highlight bindings once at definition
     * load.
     */
    public static Map<String, List<String>> compileHighlights(
        MachineDefinition machine, Screen screen) {
        validate(machine, screen);
        var groups = new ArrayList<ElementGroup>();
        for (var group : machine.groups()) {
            if (!group.automation())
                continue;
            var local = java.util.stream.IntStream
                .range(0, machine.inventorySlots(group.id()).size()).boxed()
                .toList();
            groups.add(new ElementGroup(group.id(), List.of(),
                java.util.Optional
                    .of(new ElementGroup.Members(ResourceType.ITEMS.id(),
                        group.id(), local, group.members()))));
        }
        if (machine.power() != null)
            groups.add(new ElementGroup(machine.power().id(), List.of(),
                java.util.Optional
                    .of(new ElementGroup.Members(ResourceType.ENERGY.id(),
                        machine.power().id(), List.of(0)))));
        if (machine.fluids() != null)
            for (var group : machine.fluids().groups()) {
                if (!group.automation())
                    continue;
                groups.add(new ElementGroup(group.id(), List.of(),
                    java.util.Optional
                        .of(new ElementGroup.Members(ResourceType.FLUIDS.id(),
                            machine.fluids().id(), group.members()))));
            }
        var result = new LinkedHashMap<String, List<String>>();
        GroupHighlights
            .of(screen, groups, element -> element.common().id().isPresent())
            .forEach((id, targets) -> result.put(id, targets.stream()
                .map(element -> element.common().id().orElseThrow()).toList()));
        return Collections.unmodifiableMap(result);
    }

    private static void options(Node node, Set<String> allowed) {
        for (var key : node.attributes().keySet())
            if (!Set.of("name", "generator").contains(key)
                && !allowed.contains(key))
                throw MachineDefinitions.error(node,
                    "Unknown generator option " + key);
    }

    private static Node progress(MachineDefinition machine,
        ProcessorConfiguration config, Node node) {
        var attributes = new LinkedHashMap<>(Map.of("id",
            machine.processors().size() == 1
                ? "generated_progress"
                : "generated_progress_" + config.id(),
            "value",
            machine.processors().size() == 1
                ? "#getProgress"
                : "#getProgress(" + machine.processors().indexOf(config) + ")",
            "max",
            machine.processors().size() == 1
                ? "#getTime"
                : "#getTime(" + machine.processors().indexOf(config) + ")",
            "processor-key", config.id()));
        if (config.recipeType() != null)
            attributes.put("jei-recipes", config.recipeType().id().toString());
        return n(node, "progress", attributes, List.of());
    }

    private static List<Node> processing(MachineDefinition machine, Node node) {
        return processing(machine, node,
            upgradeSlots(machine, machine.screen()));
    }

    private static List<Node> processing(MachineDefinition machine, Node node,
        Set<Integer> claimed) {
        options(node, Set.of());
        var main = new ArrayList<Node>();
        main.add(spacer(node));
        main.addAll(groups(machine, node, IoMode.INPUT, 3, claimed));
        for (var config : machine.processors())
            if (config.recipeType() != null)
                main.add(progress(machine, config, node));
        main.addAll(groups(machine, node, IoMode.OUTPUT, 3, claimed));
        main.add(spacer(node));
        var rows = new ArrayList<Node>();
        rows.add(spacer(node));
        rows.add(
            n(node, "hbox", Map.of("spacing", "6", "align", "center"), main));
        var storage = groups(machine, node, IoMode.BOTH, 3, claimed);
        if (!storage.isEmpty()) {
            storage.addFirst(spacer(node));
            storage.addLast(spacer(node));
            rows.add(n(node, "hbox", Map.of("spacing", "6"), storage));
        }
        rows.add(spacer(node));
        // The base template supplies the matching spacer before its energy
        // gauge.
        return List.of(spacer(node),
            n(node, "vbox", Map.of("spacing", "4"), rows));
    }

    private static List<Node> resources(MachineDefinition machine, Node node) {
        return resources(machine, node,
            upgradeSlots(machine, machine.screen()));
    }

    private static List<Node> resources(MachineDefinition machine, Node node,
        Set<Integer> claimed) {
        options(node, Set.of("role", "columns"));
        String role = MachineDefinitions.required(node, "role");
        IoMode mode = switch (role) {
            case "input" -> IoMode.INPUT;
            case "output" -> IoMode.OUTPUT;
            case "storage" -> IoMode.BOTH;
            default -> throw MachineDefinitions.error(node,
                "Unknown group role " + role);
        };
        int columns;
        try {
            columns = Integer
                .parseInt(node.attributes().getOrDefault("columns", "3"));
        } catch (NumberFormatException ex) {
            throw MachineDefinitions.error(node, "columns must be 1..9");
        }
        if (columns < 1 || columns > 9)
            throw MachineDefinitions.error(node, "columns must be 1..9");
        return groups(machine, node, mode, columns, claimed);
    }

    private static List<Node> tanks(MachineDefinition machine, Node node) {
        options(node, Set.of());
        var result = new ArrayList<Node>();
        if (machine.fluids() == null)
            return result;
        for (int index = 0; index < machine.fluids().tanks().size(); index++)
            result.add(tank(node, index));
        return result;
    }

    private static Node tank(Node node, int index) {
        var attributes = new LinkedHashMap<>(Map.of("id", "tank_" + index,
            "length", "3s", "tank", Integer.toString(index)));
        if (node.attributes().containsKey("tooltip"))
            attributes.put("tooltip", node.attributes().get("tooltip"));
        return n(node, "fluid-tank", attributes, List.of());
    }

    private static List<Node> groups(MachineDefinition machine, Node node,
        IoMode mode, int columns, Set<Integer> claimed) {
        var result = new ArrayList<Node>();
        if (mode == IoMode.INPUT)
            for (var ghost : machine.ghosts())
                result.add(ghostInventory(machine, node, ghost, columns));
        for (var group : machine.groups()) {
            var unclaimed = group.members().stream()
                .filter(index -> !claimed.contains(index)).toList();
            if (!unclaimed.isEmpty() && (group.mode() == mode
                || (mode == IoMode.BOTH && group.mode() == IoMode.DISABLED))) {
                var inventory = itemInventory(node, group.id(), group, columns,
                    machine.inventorySlots(group.id()), unclaimed);
                result.add(n(node, "vbox", Map.of("spacing", "2"),
                    List.of(spacer(node), inventory, spacer(node))));
            }
        }
        return result;
    }

    private static Node ghostInventory(MachineDefinition machine, Node node,
        MachineDefinition.Ghost ghost, int columns) {
        var children = new ArrayList<Node>();
        for (int i = 0; i < ghost.count(); i++)
            children.add(n(node, "ghost-slot",
                Map.of("id", "ghost_" + ghost.id() + "_" + i, "index",
                    Integer.toString(i), "tooltip",
                    machine.groupTranslationKey(ghost.id())),
                List.of()));
        return n(node, "inventory",
            Map.of("storage", ghost.id(), "columns", Integer.toString(columns)),
            children);
    }

    private static Node itemInventory(Node node, String id,
        MachineDefinition.Group group, int columns, List<Integer> members,
        List<Integer> visible) {
        var children = new ArrayList<Node>();
        for (int index : visible)
            children.add(n(node, "item-slot", Map.of("id", "slot_" + index,
                "index", Integer.toString(members.indexOf(index))), List.of()));
        var inventory = n(node, "inventory", Map.of("storage", id, "columns",
            Integer.toString(columns), "spacing", "2"), children);
        return group != null && group.automation()
            ? n(node, "coloured-group",
                Map.of("id", "inventory_" + id, "color",
                    String.format("#%06X", group.color() & 0xffffff)),
                List.of(inventory))
            : inventory;
    }

    /**
     * Only upgrade layouts reserve slots; unrelated manual slots stay visible.
     */
    private static Set<Integer> upgradeSlots(MachineDefinition machine,
        Node screen) {
        var claimed = new java.util.HashSet<Integer>();
        collectUpgradeSlots(machine, screen, null, false, claimed);
        if (!declaresUpgradeLayout(screen) && hasComponentGenerator(screen)) {
            for (int i = 0; i < machine.slots().size(); i++) {
                String id = machine.slots().get(i).id();
                if (id.equals(machine.behavior().gearboxSlot())
                    || id.equals(machine.behavior().capacitorSlot()))
                    claimed.add(i);
            }
        }
        return Set.copyOf(claimed);
    }

    private static boolean hasComponentGenerator(Node node) {
        return "mmdlib:component-upgrades"
            .equals(node.attributes().get("generator"))
            || node.children().stream()
                .anyMatch(MachineScreenGenerators::hasComponentGenerator);
    }

    private static void collectUpgradeSlots(MachineDefinition machine,
        Node node, String inventory, boolean upgrade, Set<Integer> claimed) {
        if (node.tag().equals(ScreenTags.name("machine-upgrades"))) {
            collectUpgradeSlots(machine, upgradeSockets(machine, node),
                inventory, true, claimed);
            return;
        }
        upgrade |= node.tag().equals(ScreenTags.name("upgrades-drawer"));
        if (node.tag().equals(ScreenTags.name("inventory"))) {
            inventory = node.attributes().get("storage");
            if (upgrade && node.children().isEmpty()) {
                var members = machine.inventorySlots(inventory);
                int count = Integer.parseInt(node.attributes()
                    .getOrDefault("count", Integer.toString(members.size())));
                claimed.addAll(
                    members.subList(0, Math.min(count, members.size())));
            }
        }
        if (upgrade && inventory != null
            && Set.of("slot", "item-slot", "upgrade-slot").stream()
                .anyMatch(name -> node.tag().equals(ScreenTags.name(name)))) {
            var members = machine.inventorySlots(inventory);
            int local = Integer
                .parseInt(node.attributes().getOrDefault("index", "0"));
            if (local >= 0 && local < members.size())
                claimed.add(members.get(local));
        }
        for (var child : node.children())
            collectUpgradeSlots(machine, child, inventory, upgrade, claimed);
    }

    private static Node n(Node origin, String name,
        Map<String, String> attributes, List<Node> children) {
        return new Node(name, attributes, List.copyOf(children),
            origin.source(), origin.line());
    }

    private static Node spacer(Node origin) {
        return n(origin, "spacer", Map.of(), List.of());
    }
}
