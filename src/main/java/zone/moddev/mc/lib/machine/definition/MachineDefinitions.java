package zone.moddev.mc.lib.machine.definition;

import java.io.IOException;
import java.io.UncheckedIOException;

import java.nio.file.Files;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.common.loading.DefinitionLoader.SourceRoot;
import zone.moddev.mc.lib.gui.load.ScreenException;
import zone.moddev.mc.lib.gui.load.ScreenReader.Node;
import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.machine.definition.MachineDefinition.Fluids;
import zone.moddev.mc.lib.machine.definition.MachineDefinition.Generator;
import zone.moddev.mc.lib.machine.definition.MachineDefinition.Group;
import zone.moddev.mc.lib.machine.definition.MachineDefinition.Power;
import zone.moddev.mc.lib.machine.definition.MachineDefinition.RecipeType;
import zone.moddev.mc.lib.machine.definition.MachineDefinition.Slot;
import zone.moddev.mc.lib.machine.definition.MachineDefinition.Tank;
import zone.moddev.mc.lib.machine.inventory.MachineStorageKeys;
import zone.moddev.mc.lib.machine.presentation.ConditionalFaceTexture;
import zone.moddev.mc.lib.machine.presentation.ConditionalModelPart;
import zone.moddev.mc.lib.machine.presentation.FaceOverlay;
import zone.moddev.mc.lib.machine.presentation.VisualCondition;

/**
 * Discovers machine XML, composes inheritance, then validates concrete plans
 * before registration.
 */
public final class MachineDefinitions {
    private MachineDefinitions() {
    }

    public static Map<ResourceLocation, MachineDefinition> load(
        List<SourceRoot> sources) {
        var nodes = new TreeMap<ResourceLocation, Node>();
        var loaders = new HashMap<String, ClassLoader>();
        for (var source : sources) {
            var folder = source.root().resolve("machine");
            if (!Files.isDirectory(folder))
                continue;
            try (var paths = Files.walk(folder)) {
                for (var path : paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".xml")).sorted()
                    .toList()) {
                    String relative = folder.relativize(path).toString()
                        .replace('\\', '/');
                    var id = ResourceLocation.fromNamespaceAndPath(
                        source.namespace(),
                        relative.substring(0, relative.length() - 4));
                    try (var stream = Files.newInputStream(path)) {
                        var node = ScreenReader.parseMachine(path.toString(),
                            stream);
                        validateTree(node);
                        if (!node.name().equals("machine"))
                            throw error(node, "Expected <machine>");
                        if (nodes.putIfAbsent(id, node) != null)
                            throw error(node, "Duplicate machine " + id);
                        loaders.put(node.source(), source.classLoader());
                    }
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
        var resolved = new HashMap<ResourceLocation, Node>();
        var plans = new TreeMap<ResourceLocation, MachineDefinition>();
        for (var id : nodes.keySet()) {
            var node = resolve(id, nodes, resolved, new LinkedHashSet<>());
            if (!bool(nodes.get(id), "abstract", false))
                plans.put(id, plan(id, node, loaders));
        }
        return Collections.unmodifiableMap(plans);
    }

    private static ProcessorConfiguration processorConfiguration(Node node,
        Map<ResourceLocation, Node> recipes, Set<String> resources,
        Set<String> inputs, Map<String, ClassLoader> loaders,
        String machineName) {
        try {
            String id = required(node, "id");
            long operatingCost = node.attributes()
                .containsKey("energy-per-tick")
                    ? number(node, "energy-per-tick", 0, Long.MAX_VALUE)
                    : 0;
            if (operatingCost > 0 && !resources.contains("energy"))
                throw error(node, "energy-per-tick requires energy storage");
            var bindings = new LinkedHashMap<String, String>();
            if (node.attributes().containsKey("bindings")) {
                for (String pair : required(node, "bindings").split("\\s+")) {
                    String[] parts = pair.split("=", -1);
                    if (parts.length != 2 || parts[0].isBlank()
                        || !resources.contains(parts[1])
                        || bindings.putIfAbsent(parts[0], parts[1]) != null)
                        throw error(node, "Invalid processor binding: " + pair);
                }
                if (resources.contains("energy"))
                    bindings.putIfAbsent("energy", "energy");
            } else
                resources.forEach(key -> bindings.put(key, key));
            if (node.attributes().containsKey("class")) {
                for (var option : List.of("recipe-type", "filter-inputs",
                    "selectable", "default-recipe", "running-sound",
                    "finish-sound", "on-start", "on-finish"))
                    if (node.attributes().containsKey(option))
                        throw error(node,
                            "Custom processors cannot take " + option);
                if (node.attributes().containsKey("bindings"))
                    throw error(node,
                        "Custom processors use named host resources directly");
                return new ProcessorConfiguration(id,
                    node.attributes().getOrDefault("name", machineName), null,
                    ProcessorDeclaration.load(required(node, "class"),
                        loaders.get(node.source())),
                    Map.of(), List.of(), null, MachineDefinition.Sounds.NONE,
                    MachineDefinition.Events.NONE, operatingCost);
            }
            var recipeNode = recipes.get(resource(node, "recipe-type"));
            if (recipeNode == null)
                throw error(node, "Unknown recipe type");
            var filterInputs = node.attributes().containsKey("filter-inputs")
                ? List.of(required(node, "filter-inputs").split("\\s+"))
                : bindings.entrySet().stream()
                    .filter(entry -> inputs.contains(entry.getValue()))
                    .map(Map.Entry::getKey).toList();
            var expectedInputs = bindings.entrySet().stream()
                .filter(entry -> inputs.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
            if (!new HashSet<>(filterInputs).equals(expectedInputs))
                throw error(node,
                    "filter-inputs must name all bound input groups");
            for (var key : filterInputs)
                if (!inputs.contains(bindings.get(key)))
                    throw error(node,
                        "filter-inputs must name bound input groups");
            boolean selectable = bool(node, "selectable", false);
            if (!selectable && node.attributes().containsKey("default-recipe"))
                throw error(node, "default-recipe requires selectable='true'");
            return new ProcessorConfiguration(id,
                node.attributes().getOrDefault("name", machineName),
                recipeType(recipeNode, loaders), null, bindings, filterInputs,
                selectable
                    ? new MachineDefinition.RecipeSelection(
                        optionalResource(node, "default-recipe").orElse(null))
                    : null,
                new MachineDefinition.Sounds(
                    optionalResource(node, "running-sound"),
                    optionalResource(node, "finish-sound")),
                new MachineDefinition.Events(optionalResource(node, "on-start"),
                    optionalResource(node, "on-finish")),
                operatingCost);
        } catch (IllegalArgumentException ex) {
            throw error(node, ex.getMessage());
        }
    }

    private static AdmissionDeclaration admission(Node node,
        Map<String, ClassLoader> loaders) {
        try {
            return AdmissionDeclaration.load(
                node.attributes().get("filter-class"),
                node.attributes().get("filter-processor"),
                loaders.get(node.source()));
        } catch (IllegalArgumentException ex) {
            throw error(node, ex.getMessage());
        }
    }

    private static Node resolve(ResourceLocation id,
        Map<ResourceLocation, Node> nodes, Map<ResourceLocation, Node> resolved,
        Set<ResourceLocation> visiting) {
        if (resolved.containsKey(id))
            return resolved.get(id);
        var node = nodes.get(id);
        if (node == null)
            throw new IllegalArgumentException("Unknown base machine " + id);
        if (!visiting.add(id))
            throw error(node, "Machine inheritance cycle " + visiting);
        var parent = node.attributes().get("extends");
        var result = node;
        if (parent != null) {
            var parentId = resource(node, "extends");
            if (!nodes.containsKey(parentId))
                throw error(node, "Unknown base machine " + parent);
            result = merge(resolve(parentId, nodes, resolved, visiting), node);
        }
        visiting.remove(id);
        resolved.put(id, result);
        return result;
    }

    private static String key(Node parent, Node node) {
        // These sections contain singletons; their ID is configuration, not
        // merge identity.
        if (parent.name().equals("machine")
            && Set.of("processor", "recipe-type").contains(node.name()))
            return node.name() + ":" + required(node, "id");
        if (Set.of("machine", "storage", "recipe-type").contains(parent.name()))
            return node.name();
        if (parent.name().equals("appearance") && isDisplay(node))
            return node.name();
        var id = node.attributes().getOrDefault("id",
            node.attributes().get("name"));
        // Ordinary unnamed screen siblings are ordered content, never implicit
        // overrides.
        return id == null ? null : node.name() + ":" + id;
    }

    private static boolean isDisplay(Node node) {
        return node.name().equals("item-display")
            || node.name().equals("entity-display");
    }

    private static Node merge(Node base, Node child) {
        if (child.name().equals("generator") || child.name().equals("fill"))
            return child;
        var attrs = new LinkedHashMap<>(base.attributes());
        // A new preset replaces the previous preset's base configuration, but
        // retains parts.
        if (child.name().equals("appearance")
            && child.attributes().containsKey("preset") && !child.attributes()
                .get("preset").equals(base.attributes().get("preset")))
            for (var attribute : List.of("front", "side", "top", "bottom",
                "model"))
                attrs.remove(attribute);
        attrs.putAll(child.attributes());
        var singletonOverrides = new HashSet<String>();
        if (base.name().equals("machine"))
            for (var kind : List.of("processor", "recipe-type"))
                if (base.children().stream().filter(n -> n.name().equals(kind))
                    .count() == 1
                    && child.children().stream()
                        .filter(n -> n.name().equals(kind)).count() == 1)
                    singletonOverrides.add(kind);
        var children = new ArrayList<>(base.children());
        var indexes = new HashMap<String, Integer>();
        for (int i = 0; i < children.size(); i++) {
            var key = singletonOverrides.contains(children.get(i).name())
                ? children.get(i).name()
                : key(base, children.get(i));
            if (key != null && indexes.putIfAbsent(key, i) != null)
                throw error(children.get(i), "Duplicate definition " + key);
        }
        var seen = new HashSet<String>();
        for (var node : child.children()) {
            var key = singletonOverrides.contains(node.name())
                ? node.name()
                : key(child, node);
            if (key != null && !seen.add(key))
                throw error(node, "Duplicate definition " + key);
            var index = key == null ? null : indexes.get(key);
            if (index != null)
                children.set(index, merge(children.get(index), node));
            else {
                if (key != null)
                    indexes.put(key, children.size());
                children.add(node);
            }
        }
        return new Node(child.name(), Map.copyOf(attrs), List.copyOf(children),
            child.source(), child.line(), child.namespace());
    }

    private static final Map<String, Set<String>> ATTRIBUTES = Map.ofEntries(
        Map.entry("machine", Set.of("version", "abstract", "extends", "name")),
        Map.entry("block",
            Set.of("hardness", "facing", "placement-facing", "drop", "mineable",
                "requires-tool", "stack-size", "no-occlusion", "immovable",
                "portable", "on-secondary-use", "drawers")),
        Map.entry("appearance",
            Set.of("preset", "front", "side", "top", "bottom", "model")),
        Map.entry("visualizers", Set.of("range-color", "work-color")),
        Map.entry("upgrades",
            Set.of("gearbox-slot", "capacitor-slot", "range-slots",
                "range-storage", "legacy-layout")),
        Map.entry("entity-display",
            Set.of("variable", "offset", "width", "height", "yaw", "item-yaw",
                "block", "item", "item-property")),
        Map.entry("item-display",
            Set.of("variable", "x", "y", "width", "height")),
        Map.entry("part", Set.of("id", "model", "when")),
        Map.entry("face-texture",
            Set.of("id", "face", "texture", "when", "emissive-mask")),
        Map.entry("face-overlay",
            Set.of("id", "face", "texture", "x", "y", "width", "height",
                "frames", "variable", "emissive", "when")),
        Map.entry("storage", Set.of()), Map.entry("items", Set.of("id")),
        Map.entry("fluids", Set.of("id")),
        Map.entry("tank", Set.of("id", "capacity", "role", "fluid", "fluid-tag",
            "filter-class", "filter-processor", "color", "automation", "name")),
        Map.entry("tanks",
            Set.of("id", "count", "capacity", "role", "fluid", "fluid-tag",
                "filter-class", "filter-processor", "color", "automation",
                "name")),
        Map.entry("energy",
            Set.of("id", "capacity", "input-rate", "output-rate", "color",
                "name", "provider", "capacitors")),
        Map.entry("slots",
            Set.of("id", "count", "role", "limit", "color", "type",
                "default-item", "name", "automation", "item-tag",
                "filter-class", "filter-processor")),
        Map.entry("slot",
            Set.of("id", "count", "role", "limit", "type", "default-item",
                "name", "item-tag", "filter-class", "filter-processor")),
        Map.entry("groups", Set.of()),
        Map.entry("group",
            Set.of("id", "storage", "members", "mode", "color", "name")),
        Map.entry("recipe-type", Set.of("id", "declare", "name")),
        Map.entry("processor",
            Set.of("id", "name", "recipe-type", "filter-inputs", "selectable",
                "default-recipe", "class", "running-sound", "finish-sound",
                "on-start", "on-finish", "bindings", "primary",
                "energy-per-tick")),
        Map.entry("redstone", Set.of("enabled", "pause", "comparator-source")),
        Map.entry("shape", Set.of("id", "when", "bounds")),
        Map.entry("shapes", Set.of()), Map.entry("recovery", Set.of("policy")));
    private static final Map<String, Set<String>> CHILDREN = Map.of("machine",
        Set.of("block", "appearance", "storage", "groups", "recipe-type",
            "processor", "redstone", "recovery", "screen", "shapes",
            "visualizers", "upgrades"),
        "storage", Set.of("energy", "items", "fluids"), "fluids",
        Set.of("tank", "tanks"), "items", Set.of("slot", "slots"), "groups",
        Set.of("group"), "appearance",
        Set.of("part", "face-texture", "face-overlay", "entity-display",
            "item-display"),
        "recipe-type", Set.of("generator"), "shapes", Set.of("shape"));

    private static void validateTree(Node node) {
        if (node.name().equals("screen")
            && node.namespace().equals(ScreenReader.NAMESPACE))
            return;
        if (!node.namespace().equals(ScreenReader.NAMESPACE))
            throw error(node,
                "Unknown machine XML namespace: " + node.namespace());
        if (!node.name().equals("generator")) {
            var allowed = ATTRIBUTES.get(node.name());
            if (allowed == null)
                throw error(node, "Unknown machine element " + node.name());
            for (var attr : node.attributes().keySet())
                if (!allowed.contains(attr))
                    throw error(node, "Unknown attribute " + attr);
        }
        // Validate values where they were authored, before inherited attributes
        // acquire a child
        // source.
        var resources = switch (node.name()) {
            case "machine" -> List.of("extends");
            case "block" -> List.of("mineable");
            case "appearance" ->
                List.of("front", "side", "top", "bottom", "model");
            case "part" -> List.of("model");
            case "entity-display" -> List.of("item-property");
            case "face-texture" -> List.of("texture", "emissive-mask");
            case "face-overlay" -> List.of("texture");
            case "recipe-type" -> List.of("id");
            case "processor" -> List.of("recipe-type", "default-recipe",
                "running-sound", "finish-sound", "on-start", "on-finish");
            case "tank", "tanks" -> List.of("fluid", "fluid-tag");
            case "slot", "slots" -> List.of("default-item", "item-tag");
            default -> List.<String>of();
        };
        for (var attribute : resources)
            if (node.attributes().containsKey(attribute))
                resource(node, attribute);
        if (node.name().equals("block")
            && node.attributes().containsKey("hardness"))
            hardness(node);
        if (Set.of("slots", "tank", "tanks", "energy", "group")
            .contains(node.name()))
            color(node, 0);
        if (node.name().equals("visualizers")) {
            color(node, "range-color", 0);
            color(node, "work-color", 0);
        }
        if (Set.of("slot", "slots", "tank", "tanks", "energy", "group")
            .contains(node.name()) && node.attributes().containsKey("name"))
            required(node, "name");
        var seen = new HashSet<String>();
        for (var child : node.children()) {
            if (!CHILDREN.getOrDefault(node.name(), Set.of())
                .contains(child.name()))
                throw error(child, "Unexpected child of " + node.name());
            var key = key(node, child);
            if (key != null && !seen.add(key))
                throw error(child, "Duplicate definition " + key);
            validateTree(child);
        }
    }

    private static MachineDefinition plan(ResourceLocation id, Node root,
        Map<String, ClassLoader> loaders) {
        if (!"1".equals(root.attributes().get("version")))
            throw error(root, "Expected machine version=1");
        var block = one(root, "block");

        requireChoice(block, "drop", "self");
        var appearance = one(root, "appearance");
        requireChoice(appearance, "preset", "cube-front", "model");
        var textures = new HashMap<String, String>();
        ResourceLocation baseModel = null;
        if (required(appearance, "preset").equals("model")) {
            baseModel = resource(appearance, "model");
            if (baseModel.equals(id.withPrefix("block/")) || baseModel
                .equals(id.withPrefix("block/").withSuffix("_base")))
                throw error(appearance,
                    "The base model cannot reference this machine's generated models");
            for (var face : List.of("front", "side", "top", "bottom"))
                if (appearance.attributes().containsKey(face))
                    throw error(appearance,
                        "The model preset does not support attribute " + face);
        } else {
            if (appearance.attributes().containsKey("model"))
                throw error(appearance,
                    "The cube-front preset does not support attribute model");
            for (var face : List.of("front", "side", "top"))
                textures.put(face, resource(appearance, face).toString());
            textures.put("bottom",
                appearance.attributes().containsKey("bottom")
                    ? resource(appearance, "bottom").toString()
                    : textures.get("side"));
        }
        var storage = one(root, "storage");
        var power = optional(storage, "energy");
        String energyProvider = power == null
            ? null
            : power.attributes().get("provider");
        if ("builtin".equals(energyProvider))
            energyProvider = null;
        boolean energyCapacitors = power != null
            && bool(power, "capacitors", energyProvider == null);
        var items = optional(storage, "items");
        String itemId = items == null ? null : required(items, "id");
        if (items != null && (itemId.equals("energy")
            || MachineStorageKeys.RESERVED.contains(itemId)))
            throw error(items, "Reserved storage ID " + itemId);
        String powerId = power == null ? null : required(power, "id");
        // BasePoweredMachine owns this stable save key.
        if (powerId != null && !powerId.equals("energy"))
            throw error(power, "Powered machines require energy id='energy'");
        int energyColor = power == null
            ? MachineColors.parse("energy")
            : color(power, MachineColors.parse("energy"));
        String energyName = power == null
            ? "Energy"
            : power.attributes().getOrDefault("name", "Energy");
        var fluidNode = optional(storage, "fluids");
        var tanks = new ArrayList<Tank>();
        var fluidGroups = new LinkedHashMap<String, Group>();
        var tankIds = new HashMap<String, Integer>();
        if (fluidNode != null) {
            String fluidId = required(fluidNode, "id");
            if (fluidId.equals("energy") || fluidId.equals(itemId)
                || MachineStorageKeys.RESERVED.contains(fluidId))
                throw error(fluidNode,
                    "Duplicate or reserved storage ID " + fluidId);
            for (var node : fluidNode.children()) {
                String tankId = required(node, "id");
                Role role = role(node);
                boolean automation = bool(node, "automation", true);
                if (!automation && role != Role.MANUAL)
                    throw error(node,
                        "Tanks without automation must use role=manual");
                int count = node.name().equals("tanks")
                    ? (int) number(node, "count", 1, 16)
                    : 1;
                int capacity = (int) number(node, "capacity", 1,
                    Integer.MAX_VALUE);
                var filter = node.attributes().containsKey("fluid")
                    ? resource(node, "fluid")
                    : null;
                var members = new ArrayList<Integer>();
                for (int i = 0; i < count; i++) {
                    String member = node.name().equals("tanks")
                        ? tankId + "/" + i
                        : tankId;
                    if (tankIds.putIfAbsent(member, tanks.size()) != null)
                        throw error(node, "Duplicate tank " + member);
                    members.add(tanks.size());
                    tanks.add(new Tank(member, role, capacity, filter,
                        optionalResource(node, "fluid-tag").orElse(null),
                        admission(node, loaders)));
                }
                if (fluidGroups.putIfAbsent(tankId, new Group(tankId, members,
                    mode(role),
                    color(node, MachineColors.fluid(fluidGroups.size())),
                    automation,
                    node.attributes().getOrDefault("name", tankId))) != null)
                    throw error(node, "Duplicate fluid group " + tankId);
            }
            if (tanks.isEmpty() || tanks.size() > 16)
                throw error(fluidNode, "Expected 1..16 tanks");
        }
        var slots = new ArrayList<Slot>();
        var ghosts = new ArrayList<MachineDefinition.Ghost>();
        var groups = new LinkedHashMap<String, Group>();
        var ids = new HashMap<String, Integer>();
        for (var node : items == null ? List.<Node>of() : items.children()) {
            String slotId = required(node, "id");
            Role role;
            try {
                role = Role
                    .valueOf(required(node, "role").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw error(node, "Invalid slot role");
            }
            int count = node.name().equals("slots")
                ? (int) number(node, "count", 1, 54)
                : node.attributes().containsKey("count")
                    ? (int) number(node, "count", 1, 1)
                    : 1;
            String slotType = node.attributes().getOrDefault("type", "item");
            if (!Set.of("item", "ghost").contains(slotType))
                throw error(node, "Slot type must be item or ghost");
            if (slotType.equals("ghost")) {
                if (node.attributes().containsKey("item-tag")
                    || node.attributes().containsKey("filter-class")
                    || node.attributes().containsKey("filter-processor"))
                    throw error(node,
                        "Storage filters are only supported for real item slots");
                if (node.attributes().containsKey("automation"))
                    throw error(node, "Ghost slots do not expose automation");
                if (role != Role.INPUT
                    || node.attributes().containsKey("limit"))
                    throw error(node,
                        "Ghost slots require role=input and always hold one sample");
                if (ghosts.stream()
                    .anyMatch(ghost -> ghost.id().equals(slotId)))
                    throw error(node, "Duplicate ghost inventory " + slotId);
                ghosts.add(new MachineDefinition.Ghost(slotId, count,
                    node.attributes().containsKey("default-item")
                        ? resource(node, "default-item")
                        : null,
                    node.attributes().getOrDefault("name", slotId)));
                continue;
            }
            if (node.name().equals("slot")
                && node.attributes().containsKey("name"))
                throw error(node,
                    "A standalone item slot's name belongs on its group");
            if (node.attributes().containsKey("default-item"))
                throw error(node,
                    "default-item is only supported for ghost slots");
            int limit = node.attributes().containsKey("limit")
                ? (int) number(node, "limit", 1, 64)
                : 64;
            var members = new ArrayList<Integer>();
            for (int i = 0; i < count; i++) {
                String memberId = node.name().equals("slots")
                    ? slotId + "/" + i
                    : slotId;
                if (ids.putIfAbsent(memberId, slots.size()) != null)
                    throw error(node, "Duplicate slot " + memberId);
                members.add(slots.size());
                slots.add(new Slot(memberId, role, limit,
                    node.attributes().containsKey("item-tag")
                        ? resource(node, "item-tag")
                        : null,
                    admission(node, loaders)));
            }
            if (node.name().equals("slots")) {
                boolean automation = bool(node, "automation", true);
                if (!automation && role != Role.MANUAL)
                    throw error(node,
                        "Slots without automation must use role=manual");
                IoMode mode = switch (role) {
                    case INPUT -> IoMode.INPUT;
                    case OUTPUT -> IoMode.OUTPUT;
                    case STORAGE -> IoMode.BOTH;
                    case MANUAL -> IoMode.DISABLED;
                };
                if (groups.putIfAbsent(slotId, new Group(slotId, members, mode,
                    color(node, defaultItemColor(groups, mode)), automation,
                    node.attributes().getOrDefault("name", slotId))) != null)
                    throw error(node, "Duplicate group " + slotId);
            }
        }
        int totalSlots = slots.size()
            + ghosts.stream().mapToInt(MachineDefinition.Ghost::count).sum();
        if (items != null && (totalSlots == 0 || totalSlots > 54))
            throw error(items, "Expected 1..54 item slots");
        var groupsNode = optional(root, "groups");
        if (groupsNode != null)
            for (var node : groupsNode.children()) {
                String groupId = required(node, "id");
                if (required(node, "storage").equals(powerId)) {
                    if (!groupId.equals(powerId)
                        || !"input".equals(required(node, "mode")))
                        throw error(node, "Energy group must be energy/input");
                    if (node.attributes().containsKey("members"))
                        throw error(node,
                            "Energy group has a single implicit member");
                    energyColor = color(node, energyColor);
                    energyName = node.attributes().getOrDefault("name",
                        energyName);
                    continue;
                }
                if (fluidNode != null && required(node, "storage")
                    .equals(required(fluidNode, "id"))) {
                    var prior = fluidGroups.get(groupId);
                    if (prior == null)
                        throw error(node,
                            "Unknown automatic fluid group " + groupId);
                    if (node.attributes().containsKey("members")) {
                        var members = new ArrayList<Integer>();
                        for (var member : required(node, "members")
                            .split("\\s+")) {
                            if (!tankIds.containsKey(member))
                                throw error(node, "Unknown tank " + member);
                            members.add(tankIds.get(member));
                        }
                        if (!prior.members().equals(members))
                            throw error(node,
                                "Conflicting automatic fluid group " + groupId);
                    }
                    IoMode mode;
                    try {
                        mode = IoMode.valueOf(
                            required(node, "mode").toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        throw error(node, "Invalid group mode");
                    }
                    fluidGroups.put(groupId, new Group(groupId, prior.members(),
                        mode, color(node, prior.color()), prior.automation(),
                        node.attributes().getOrDefault("name", prior.name())));
                    continue;
                }
                if (!required(node, "storage").equals(itemId))
                    throw error(node, "Unknown storage");
                var prior = groups.get(groupId);
                var members = new ArrayList<Integer>();
                if (node.attributes().containsKey("members"))
                    for (String member : required(node, "members")
                        .split("\\s+")) {
                        if (!ids.containsKey(member))
                            throw error(node, "Unknown slot " + member);
                        members.add(ids.get(member));
                    }
                else if (prior != null)
                    members.addAll(prior.members());
                if (members.isEmpty())
                    throw error(node, "Empty group " + groupId);
                if (prior != null && !prior.members().equals(members))
                    throw error(node, "Conflicting automatic group " + groupId);
                IoMode mode;
                try {
                    mode = IoMode.valueOf(
                        required(node, "mode").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    throw error(node, "Invalid group mode");
                }
                groups.put(groupId,
                    new Group(groupId, members, mode,
                        color(node,
                            prior == null
                                ? defaultItemColor(groups, mode)
                                : prior.color()),
                        prior == null || prior.automation(),
                        node.attributes().getOrDefault("name",
                            prior == null ? groupId : prior.name())));
            }
        var covered = new HashSet<Integer>();
        for (var group : groups.values())
            for (int index : group.members()) {
                if (!covered.add(index))
                    throw error(items, "Overlapping item groups");
                var role = slots.get(index).role();
                if ((group.mode().canInsert() && !role.canInsert())
                    || (group.mode().canExtract() && !role.canExtract()))
                    throw error(items,
                        "Group mode conflicts with slot role: " + group.id());
            }
        if (covered.size() != slots.size())
            throw error(items, "Every slot must belong to a group");
        if (groups.containsKey(powerId))
            throw error(items, "Item group conflicts with energy");
        for (var ghost : ghosts)
            if (groups.containsKey(ghost.id()) || ids.containsKey(ghost.id())
                || fluidGroups.containsKey(ghost.id())
                || ghost.id().equals(powerId) || ghost.id().equals(itemId)
                || MachineStorageKeys.RESERVED.contains(ghost.id()))
                throw error(items,
                    "Ghost inventory conflicts with another resource: "
                        + ghost.id());
        for (var group : fluidGroups.values()) {
            if (groups.containsKey(group.id()) || group.id().equals(powerId))
                throw error(fluidNode,
                    "Duplicate resource group " + group.id());
            for (int index : group.members()) {
                var role = tanks.get(index).role();
                if ((group.mode().canInsert() && !role.canInsert())
                    || (group.mode().canExtract() && !role.canExtract()))
                    throw error(fluidNode,
                        "Group mode conflicts with tank role: " + group.id());
            }
        }
        var resourceKeys = new LinkedHashSet<String>();
        resourceKeys.addAll(groups.keySet());
        resourceKeys.addAll(fluidGroups.keySet());
        ghosts.forEach(ghost -> resourceKeys.add(ghost.id()));
        if (power != null)
            resourceKeys.add("energy");
        var inputKeys = new LinkedHashSet<String>();
        groups.values().stream().filter(g -> g.mode() == IoMode.INPUT)
            .forEach(g -> inputKeys.add(g.id()));
        fluidGroups.values().stream().filter(g -> g.mode() == IoMode.INPUT)
            .forEach(g -> inputKeys.add(g.id()));
        var processorNodes = root.children().stream()
            .filter(n -> n.name().equals("processor")).toList();
        var primaryNodes = processorNodes.stream()
            .filter(n -> bool(n, "primary", false)).toList();
        if (primaryNodes.size() > 1)
            throw error(root, "Only one primary processor is allowed");
        if (processorNodes.size() > 1 && primaryNodes.isEmpty()
            && (bool(block, "portable", false)
                || block.attributes().containsKey("on-secondary-use")))
            throw error(block,
                "Multiple processors require an explicit primary for portable/secondary actions");
        var processor = primaryNodes.isEmpty()
            ? (processorNodes.isEmpty() ? null : processorNodes.getFirst())
            : primaryNodes.getFirst();
        var recipeNodes = new LinkedHashMap<ResourceLocation, Node>();
        for (var node : root.children())
            if (node.name().equals("recipe-type")
                && recipeNodes.putIfAbsent(resource(node, "id"), node) != null)
                throw error(node, "Duplicate recipe type");
        var processorConfigs = new ArrayList<ProcessorConfiguration>();
        var processorIds = new HashSet<String>();
        for (var node : processorNodes) {
            var config = processorConfiguration(node, recipeNodes, resourceKeys,
                inputKeys, loaders, required(root, "name"));
            if (!processorIds.add(config.id()))
                throw error(node, "Duplicate processor ID " + config.id());
            processorConfigs.add(config);
        }
        var primary = processor == null
            ? null
            : processorConfigs.stream()
                .filter(config -> config.id().equals(required(processor, "id")))
                .findFirst().orElseThrow();
        boolean custom = primary != null && primary.declaration() != null;
        var recipeType = primary == null ? null : primary.recipeType();
        if (processor == null) {
            if (!recipeNodes.isEmpty())
                throw error(root, "recipe-type requires a processor");
            if (slots.stream().anyMatch(
                slot -> slot.role() == Role.INPUT || slot.role() == Role.OUTPUT)
                || !ghosts.isEmpty())
                throw error(items,
                    "Machines without a processor require storage/manual item roles");
            if (tanks.stream().anyMatch(tank -> tank.role() == Role.INPUT
                || tank.role() == Role.OUTPUT))
                throw error(fluidNode,
                    "Machines without a processor require storage/manual tank roles");
        }
        var redstone = optional(root, "redstone");
        if (redstone != null)
            requireChoice(redstone, "pause", "processing-only");
        var recovery = one(root, "recovery");
        requireChoice(recovery, "policy", "capsule");
        var screen = one(root, "screen");
        var attrs = new HashMap<>(screen.attributes());
        attrs.put("title", "block." + id.getNamespace() + "."
            + id.getPath().replace('/', '.'));
        attrs.put("title-fallback", required(root, "name"));
        attrs.put("block-entity",
            "zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity");
        float hardness = hardness(block);
        var modelParts = new ArrayList<ConditionalModelPart>();
        var faceTextures = new ArrayList<ConditionalFaceTexture>();
        var appearanceRules = appearance.children().stream()
            .filter(part -> !isDisplay(part)).toList();
        if (appearanceRules.size() > 64)
            throw error(appearance,
                "At most 64 conditional parts and face-texture rules are supported");
        for (var part : appearanceRules) {
            if (part.name().equals("face-overlay")) {
                modelParts.addAll(overlayParts(part));
                continue;
            }
            if (part.name().equals("face-texture")) {
                if (baseModel != null)
                    throw error(part,
                        "The model preset only supports part children, not face-texture");
                RelativeFace face;
                try {
                    face = RelativeFace.valueOf(
                        required(part, "face").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    throw error(part,
                        "Expected face front, back, left, right, top or bottom");
                }
                faceTextures
                    .add(new ConditionalFaceTexture(required(part, "id"), face,
                        resource(part, "texture"), required(part, "when"),
                        part.attributes().containsKey("emissive-mask")
                            ? resource(part, "emissive-mask")
                            : null));
                continue;
            }
            if (required(part, "id").equals("base"))
                throw error(part, "The model part ID 'base' is reserved");
            if (resource(part, "model").equals(id.withPrefix("block/")))
                throw error(part, "A conditional model cannot include itself");
            modelParts.add(new ConditionalModelPart(required(part, "id"),
                resource(part, "model"), required(part, "when")));
        }
        boolean selectable = processor != null
            && bool(processor, "selectable", false);
        if (processor != null && !selectable
            && processor.attributes().containsKey("default-recipe"))
            throw error(processor, "default-recipe requires selectable='true'");
        var shapeNode = optional(root, "shapes");
        var shapes = new ArrayList<MachineBehavior.Shape>();
        if (shapeNode != null)
            for (var shape : shapeNode.children()) {
                var bounds = required(shape, "bounds").trim().split("\\s+");
                if (bounds.length != 6)
                    throw error(shape, "Shape bounds require six coordinates");
                try {
                    double[] v = Arrays.stream(bounds)
                        .mapToDouble(Double::parseDouble).toArray();
                    shapes.add(new MachineBehavior.Shape(required(shape, "id"),
                        required(shape, "when"), v[0], v[1], v[2], v[3], v[4],
                        v[5]));
                } catch (IllegalArgumentException ex) {
                    throw error(shape, ex.getMessage());
                }
            }
        var displayNode = optional(appearance, "entity-display");
        EntityDisplay display = null;
        if (displayNode != null) {
            var offset = displayNode.attributes()
                .getOrDefault("offset", "0.5 0 0.5").trim().split("\\s+");
            if (offset.length != 3)
                throw error(displayNode,
                    "Entity display offset needs three coordinates");
            try {
                display = new EntityDisplay(
                    zone.moddev.mc.lib.gui.model.ProcessorVariable
                        .parse(required(displayNode, "variable")),
                    Double.parseDouble(offset[0]),
                    Double.parseDouble(offset[1]),
                    Double.parseDouble(offset[2]),
                    Float.parseFloat(
                        displayNode.attributes().getOrDefault("width", "1")),
                    Float.parseFloat(
                        displayNode.attributes().getOrDefault("height", "1")),
                    Float.parseFloat(
                        displayNode.attributes().getOrDefault("yaw", "0")),
                    Float.parseFloat(displayNode.attributes()
                        .getOrDefault("item-yaw", "180")),
                    bool(displayNode, "block", true),
                    bool(displayNode, "item", true),
                    displayNode.attributes().containsKey("item-property")
                        ? resource(displayNode, "item-property")
                        : null);
            } catch (IllegalArgumentException ex) {
                throw error(displayNode, ex.getMessage());
            }
        }
        var itemDisplayNode = optional(appearance, "item-display");
        ItemDisplay itemDisplay = null;
        if (itemDisplayNode != null) {
            try {
                itemDisplay = new ItemDisplay(
                    zone.moddev.mc.lib.gui.model.ProcessorVariable
                        .parse(required(itemDisplayNode, "variable")),
                    Double.parseDouble(
                        itemDisplayNode.attributes().getOrDefault("x", "0.5")),
                    Double.parseDouble(
                        itemDisplayNode.attributes().getOrDefault("y", "0.5")),
                    Float.parseFloat(itemDisplayNode.attributes()
                        .getOrDefault("width", "0.5")),
                    Float.parseFloat(itemDisplayNode.attributes()
                        .getOrDefault("height", "0.5")));
            } catch (IllegalArgumentException ex) {
                throw error(itemDisplayNode, ex.getMessage());
            }
        }
        ProcessorDeclaration declaration = primary == null
            ? null
            : primary.declaration();
        zone.moddev.mc.lib.machine.feature.Processor<?> prototype = declaration == null
            ? null
            : declaration.schema(primary.id());
        var schemas = new LinkedHashMap<String, zone.moddev.mc.lib.machine.feature.Processor<?>>();
        processorConfigs.forEach(config -> {
            if (config.declaration() != null)
                schemas.put(config.id(),
                    config.declaration().schema(config.id()));
        });
        var visualizers = optional(root, "visualizers");
        var visualizerStyle = zone.moddev.mc.lib.machine.presentation.VisualizerStyle.DEFAULT;
        if (visualizers != null) {
            visualizerStyle = new zone.moddev.mc.lib.machine.presentation.VisualizerStyle(
                color(visualizers, "range-color", visualizerStyle.rangeColor()),
                color(visualizers, "work-color", visualizerStyle.workColor()));
        }
        if (energyProvider != null) {
            final String provider = energyProvider;
            if (processorConfigs.stream()
                .noneMatch(config -> config.id().equals(provider)
                    && config.declaration() != null
                    && zone.moddev.mc.lib.machine.feature.ProcessorEnergy.class
                        .isAssignableFrom(config.declaration().type())))
                throw error(power,
                    "Energy provider must name a processor implementing ProcessorEnergy: "
                        + provider);
        }
        var upgrades = optional(root, "upgrades");
        String gearboxSlot = upgrades == null
            ? null
            : upgrades.attributes().get("gearbox-slot");
        String capacitorSlot = upgrades == null
            ? null
            : upgrades.attributes().get("capacitor-slot");
        // The library owns standard sockets; machine storage declares only
        // extras.
        if (upgrades != null) {
            for (String reserved : List.of("mmdlib_gearbox",
                "mmdlib_capacitor"))
                if (fluidGroups.containsKey(reserved) || ghosts.stream()
                    .anyMatch(ghost -> ghost.id().equals(reserved)))
                    throw error(upgrades,
                        "Reserved component storage: " + reserved);
            if (gearboxSlot == null && processor != null)
                gearboxSlot = componentSocket(root, slots, groups, ids,
                    "gearbox");
            if (capacitorSlot == null && power != null && energyCapacitors)
                capacitorSlot = componentSocket(root, slots, groups, ids,
                    "capacitor");
            if (itemId == null && !slots.isEmpty())
                itemId = "items";
        }
        List<String> rangeSlots = upgrades != null
            && upgrades.attributes().containsKey("range-slots")
                ? List.of(
                    required(upgrades, "range-slots").trim().split("[\\s,]+"))
                : List.of();
        if (upgrades != null
            && upgrades.attributes().containsKey("range-storage")) {
            if (!rangeSlots.isEmpty())
                throw error(upgrades,
                    "Use range-storage or range-slots, not both");
            var group = groups.get(required(upgrades, "range-storage"));
            if (group == null)
                throw error(upgrades, "Unknown range-storage");
            rangeSlots = group.members().stream()
                .map(index -> slots.get(index).id()).toList();
        }
        if (rangeSlots.size() > 9
            || new java.util.HashSet<>(rangeSlots).size() != rangeSlots.size())
            throw error(upgrades,
                "range-slots requires at most nine distinct slots");
        for (String rangeSlot : rangeSlots) {
            var index = ids.get(rangeSlot);
            if (index == null || slots.get(index).role() != Role.MANUAL)
                throw error(upgrades,
                    "range-slots must name real manual item slots");
            if (rangeSlot.equals(gearboxSlot)
                || rangeSlot.equals(capacitorSlot))
                throw error(upgrades,
                    "Range sockets cannot overlap component sockets");
            if (groups.values().stream().anyMatch(
                group -> group.automation() && group.members().contains(index)))
                throw error(upgrades, "range-slots must not expose automation");
            var slot = slots.get(index);
            slots.set(index, new Slot(slot.id(), slot.role(), 1, slot.itemTag(),
                slot.admission()));
        }
        if (capacitorSlot != null && !energyCapacitors)
            throw error(upgrades,
                "Selected energy provider does not enable capacitor upgrades");
        if (capacitorSlot != null && power == null)
            throw error(upgrades, "capacitor-slot requires energy storage");
        if (capacitorSlot != null && capacitorSlot.equals(gearboxSlot))
            throw error(upgrades,
                "Gearbox and capacitor require different slots");
        String legacyUpgradeStorage = upgrades == null
            ? null
            : upgrades.attributes().get("legacy-layout");
        if (legacyUpgradeStorage != null
            && (!"mmdlib_gearbox/0".equals(gearboxSlot)
                || !"mmdlib_capacitor/0".equals(capacitorSlot)
                || rangeSlots.size() != 9 || !legacyUpgradeStorage
                    .equals(upgrades.attributes().get("range-storage"))))
            throw error(upgrades,
                "legacy-layout requires automatic components and nine range-storage slots in the same named inventory");
        for (String attribute : List.of("gearbox-slot", "capacitor-slot")) {
            if (upgrades == null
                || !upgrades.attributes().containsKey(attribute))
                continue;
            var index = ids.get(required(upgrades, attribute));
            if (index == null || slots.get(index).role() != Role.MANUAL)
                throw error(upgrades,
                    attribute + " must name a real manual item slot");
            if (groups.values().stream().anyMatch(
                group -> group.automation() && group.members().contains(index)))
                throw error(upgrades,
                    attribute + " must not expose automation");
            var slot = slots.get(index);
            slots.set(index, new Slot(slot.id(), slot.role(), 8, slot.itemTag(),
                slot.admission()));
        }
        MachineBehavior behavior;
        try {
            behavior = new MachineBehavior(declaration,
                (int) (block.attributes().containsKey("stack-size")
                    ? number(block, "stack-size", 1, 64)
                    : 64),
                bool(block, "no-occlusion", false),
                bool(block, "immovable", false), bool(block, "portable", false),
                bool(block, "drawers", true), shapes,
                block.attributes().containsKey("on-secondary-use")
                    ? resource(block, "on-secondary-use")
                    : null,
                display, itemDisplay, visualizerStyle, gearboxSlot,
                capacitorSlot, rangeSlots, legacyUpgradeStorage,
                MachineFacing.parse(
                    block.attributes().getOrDefault("facing", "horizontal"),
                    block.attributes().getOrDefault("placement-facing",
                        "toward-player")),
                redstone == null
                    ? null
                    : redstone.attributes().get("comparator-source"),
                processorConfigs.size() == 1
                    && !processor.attributes().containsKey("bindings")
                    && processorConfigs.getFirst().energyPerTick() == 0
                        ? List.of()
                        : processorConfigs);
        } catch (IllegalArgumentException ex) {
            throw error(block, ex.getMessage());
        }
        var admissions = new ArrayList<AdmissionDeclaration>();
        slots.forEach(slot -> {
            if (slot.admission() != null)
                admissions.add(slot.admission());
        });
        tanks.forEach(tank -> {
            if (tank.admission() != null)
                admissions.add(tank.admission());
        });
        for (var admission : admissions)
            if (admission.processor() != null && processorConfigs.stream()
                .noneMatch(config -> config.id().equals(admission.processor())
                    && config.declaration() != null
                    && zone.moddev.mc.lib.machine.feature.StorageAdmission.class
                        .isAssignableFrom(config.declaration().type())))
                throw error(root,
                    "filter-processor must name a processor implementing StorageAdmission: "
                        + admission.processor());
        if (behavior.secondaryAction() != null && (prototype == null
            || !prototype.actions().containsKey(behavior.secondaryAction())))
            throw error(block, "Unknown secondary-use processor action: "
                + behavior.secondaryAction());
        if (display != null) {
            if (schemas.isEmpty() || (display.item() && !behavior.portable()))
                throw error(displayNode,
                    "Entity displays require a custom processor; item displays require portable machines");
            try {
                display.validate(schemas);
            } catch (IllegalArgumentException ex) {
                throw error(displayNode, ex.getMessage());
            }
        }
        if (itemDisplay != null) {
            if (schemas.isEmpty())
                throw error(itemDisplayNode,
                    "Item displays require a custom processor with a synchronized variable");
            try {
                itemDisplay.validate(schemas);
            } catch (IllegalArgumentException ex) {
                throw error(itemDisplayNode, ex.getMessage());
            }
        }
        var definition = new MachineDefinition(id, checksum(root),
            required(root, "name"), hardness, resource(block, "mineable"),
            bool(block, "requires-tool", false), textures, baseModel,
            modelParts, faceTextures,
            power == null
                ? null
                : new Power(powerId,
                    number(power, "capacity", 1, Long.MAX_VALUE),
                    number(power, "input-rate", 0, Long.MAX_VALUE),
                    number(power, "output-rate", 0, Long.MAX_VALUE),
                    energyColor, energyName, energyProvider, energyCapacitors),
            fluidNode == null
                ? null
                : new Fluids(required(fluidNode, "id"), tanks,
                    List.copyOf(fluidGroups.values())),
            itemId, slots, List.copyOf(groups.values()), recipeType,
            processor == null ? null : required(processor, "id"),
            processor == null
                ? null
                : processor.attributes().getOrDefault("name",
                    required(root, "name")),
            redstone != null && bool(redstone, "enabled", false),
            new Node("screen", attrs, screen.children(), screen.source(),
                screen.line()),
            selectable
                ? new MachineDefinition.RecipeSelection(
                    processor.attributes().containsKey("default-recipe")
                        ? resource(processor, "default-recipe")
                        : null)
                : null,
            ghosts, behavior,
            processor == null
                ? MachineDefinition.Sounds.NONE
                : new MachineDefinition.Sounds(
                    optionalResource(processor, "running-sound"),
                    optionalResource(processor, "finish-sound")),
            processor == null
                ? MachineDefinition.Events.NONE
                : new MachineDefinition.Events(
                    optionalResource(processor, "on-start"),
                    optionalResource(processor, "on-finish")));
        var defaults = definition.visualDefaults(prototype);
        for (var part : appearanceRules) {
            try {
                if (part.name().equals("face-overlay")) {
                    for (var frame : overlayParts(part))
                        VisualCondition.compile(frame.when(), defaults);
                } else
                    VisualCondition.compile(required(part, "when"), defaults);
            } catch (IllegalArgumentException ex) {
                throw error(part, ex.getMessage());
            }
        }
        var processorDefaults = defaults.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith("processors."))
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                Map.Entry::getValue));
        if (shapeNode != null)
            for (var shape : shapeNode.children()) {
                try {
                    VisualCondition.compile(required(shape, "when"),
                        processorDefaults);
                } catch (IllegalArgumentException ex) {
                    throw error(shape, ex.getMessage());
                }
            }
        return definition;
    }

    private static RecipeType recipeType(Node recipe,
        Map<String, ClassLoader> loaders) {
        var generatorNode = optional(recipe, "generator");
        Generator generator = null;
        if (generatorNode != null) {
            var props = new HashMap<>(generatorNode.attributes());
            props.remove("class");
            generator = new Generator(required(generatorNode, "class"), props,
                generatorNode.source(), generatorNode.line(),
                loaders.get(generatorNode.source()));
        }
        var recipeId = resource(recipe, "id");
        var recipeType = new RecipeType(recipeId,
            bool(recipe, "declare", false),
            recipe.attributes().getOrDefault("name", recipeId.getPath()),
            generator);
        if (!recipeType.declare() && generator != null)
            throw error(recipe,
                "Only a declaring recipe type may supply a generator");
        return recipeType;
    }

    private static java.util.Optional<ResourceLocation> optionalResource(
        Node node, String key) {
        return node.attributes().containsKey(key)
            ? java.util.Optional.of(resource(node, key))
            : java.util.Optional.empty();
    }

    public static String required(Node node, String key) {
        var value = node.attributes().get(key);
        if (value == null || value.isBlank())
            throw error(node, "Missing " + key);
        return value;
    }

    private static Role role(Node node) {
        try {
            return Role
                .valueOf(required(node, "role").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw error(node, "Invalid role");
        }
    }

    private static IoMode mode(Role role) {
        return switch (role) {
            case INPUT -> IoMode.INPUT;
            case OUTPUT -> IoMode.OUTPUT;
            case STORAGE -> IoMode.BOTH;
            case MANUAL -> IoMode.DISABLED;
        };
    }

    public static ResourceLocation resource(Node node, String key) {
        var value = required(node, key);
        var id = ResourceLocation.tryParse(value);
        if (id == null)
            throw error(node, "Invalid " + key + "='" + value
                + "': expected a resource location such as minecraft:stone");
        return id;
    }

    private static float hardness(Node node) {
        var value = required(node, "hardness");
        try {
            float parsed = Float.parseFloat(value);
            if (Float.isFinite(parsed) && parsed >= 0)
                return parsed;
        } catch (NumberFormatException ignored) {
            // Report a definition error with the original file, line, attribute
            // and value.
        }
        throw error(node, "Invalid hardness='" + value
            + "': expected a finite nonnegative number; use '.' as the decimal separator (for"
            + " example 3.5)");
    }

    /**
     * CRC of the resolved XML tree: includes inherited settings, excludes
     * paths/formatting.
     */
    private static String checksum(Node root) {
        var text = new StringBuilder();
        canonical(root, text);
        var crc = new java.util.zip.CRC32();
        crc.update(
            text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return String.format(java.util.Locale.ROOT, "%08x", crc.getValue());
    }

    private static void canonical(Node node, StringBuilder text) {
        token(text, node.namespace());
        token(text, node.name());
        text.append(node.attributes().size()).append(':');
        new TreeMap<>(node.attributes()).forEach((key, value) -> {
            token(text, key);
            token(text, value);
        });
        text.append(node.children().size()).append(':');
        node.children().forEach(child -> canonical(child, text));
    }

    private static void token(StringBuilder text, String value) {
        text.append(value.length()).append(':').append(value);
    }

    private static Node optional(Node node, String name) {
        var children = node.children().stream()
            .filter(n -> n.name().equals(name)).toList();
        if (children.size() > 1)
            throw error(node, "Only one " + name + " is supported");
        return children.isEmpty() ? null : children.getFirst();
    }

    private static Node one(Node node, String name) {
        var result = optional(node, name);
        if (result == null)
            throw error(node, "Missing " + name);
        return result;
    }

    private static void requireChoice(Node node, String name,
        String... values) {
        if (!List.of(values).contains(required(node, name)))
            throw error(node,
                "Supported " + name + ": " + String.join(", ", values));
    }

    private static List<ConditionalModelPart> overlayParts(Node node) {
        try {
            String id = required(node, "id");
            if (id.equals("base"))
                throw new IllegalArgumentException(
                    "The model part ID 'base' is reserved");
            var face = RelativeFace
                .valueOf(required(node, "face").toUpperCase(Locale.ROOT));
            int frames = node.attributes().containsKey("frames")
                ? (int) number(node, "frames", 1, 32)
                : 1;
            String variable = frames > 1
                ? required(node, "variable")
                : node.attributes().get("variable");
            if (variable != null)
                zone.moddev.mc.lib.gui.model.ProcessorVariable.parse(variable);
            var result = new ArrayList<ConditionalModelPart>();
            for (int frame = 0; frame < frames; frame++) {
                String when = node.attributes().getOrDefault("when", "true");
                if (frames > 1)
                    when = "(" + when + ") && "
                        + (frame == 0
                            ? variable + " < 1"
                            : frame == frames - 1
                                ? variable + " >= " + frame
                                : variable + " >= " + frame + " && " + variable
                                    + " < " + (frame + 1));
                var overlay = new FaceOverlay(face, resource(node, "texture"),
                    Double.parseDouble(required(node, "x")),
                    Double.parseDouble(required(node, "y")),
                    Double.parseDouble(required(node, "width")),
                    Double.parseDouble(required(node, "height")), frame, frames,
                    bool(node, "emissive", false));
                result.add(new ConditionalModelPart(id + "/frame_" + frame,
                    null, when, overlay));
            }
            return result;
        } catch (IllegalArgumentException ex) {
            throw error(node, ex.getMessage());
        }
    }

    private static boolean bool(Node node, String key, boolean fallback) {
        var value = node.attributes().get(key);
        if (value == null)
            return fallback;
        if (!value.equals("true") && !value.equals("false"))
            throw error(node, "Invalid boolean " + key);
        return Boolean.parseBoolean(value);
    }

    private static long number(Node node, String key, long min, long max) {
        try {
            long value = Long.parseLong(required(node, key));
            if (value >= min && value <= max)
                return value;
        } catch (NumberFormatException ex) {
            /* source-aware error below */
        }
        throw error(node, key + " must be in " + min + ".." + max);
    }

    private static String componentSocket(Node origin, List<Slot> slots,
        Map<String, Group> groups, Map<String, Integer> ids, String kind) {
        String inventory = "mmdlib_" + kind;
        String id = inventory + "/0";
        if (groups.containsKey(inventory) || ids.containsKey(id))
            throw error(origin, "Reserved component storage: " + inventory);
        int index = slots.size();
        slots.add(new Slot(id, Role.MANUAL, 8,
            ResourceLocation.parse("mmdlib:" + kind + "_upgrades")));
        ids.put(id, index);
        groups.put(inventory, new Group(inventory, List.of(index),
            IoMode.DISABLED, 0xffffff, false, kind));
        return id;
    }

    private static int defaultItemColor(Map<String, Group> groups,
        IoMode mode) {
        int ordinal = (int) groups.values().stream()
            .filter(group -> group.mode() == mode).count();
        return MachineColors.item(mode, ordinal);
    }

    private static int color(Node node, int fallback) {
        return color(node, "color", fallback);
    }

    private static int color(Node node, String attribute, int fallback) {
        var value = node.attributes().get(attribute);
        if (value == null)
            return fallback;
        try {
            return MachineColors.parse(value);
        } catch (IllegalArgumentException ex) {
            throw error(node, ex.getMessage());
        }
    }

    public static ScreenException error(Node node, String message) {
        return new ScreenException(node.source(), node.line(), message);
    }
}
