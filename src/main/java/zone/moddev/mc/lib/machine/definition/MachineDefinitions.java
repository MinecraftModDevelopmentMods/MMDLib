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
        if (Set.of("machine", "storage", "recipe-type").contains(parent.name()))
            return node.name();
        var id = node.attributes().getOrDefault("id",
            node.attributes().get("name"));
        // Ordinary unnamed screen siblings are ordered content, never implicit
        // overrides.
        return id == null ? null : node.name() + ":" + id;
    }

    private static Node merge(Node base, Node child) {
        if (child.name().equals("generator") || child.name().equals("fill"))
            return child;
        var attrs = new LinkedHashMap<>(base.attributes());
        attrs.putAll(child.attributes());
        var children = new ArrayList<>(base.children());
        var indexes = new HashMap<String, Integer>();
        for (int i = 0; i < children.size(); i++) {
            var key = key(base, children.get(i));
            if (key != null && indexes.putIfAbsent(key, i) != null)
                throw error(children.get(i), "Duplicate definition " + key);
        }
        var seen = new HashSet<String>();
        for (var node : child.children()) {
            var key = key(child, node);
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
            Set.of("hardness", "facing", "drop", "mineable", "requires-tool")),
        Map.entry("appearance",
            Set.of("preset", "front", "side", "top", "bottom")),
        Map.entry("storage", Set.of()), Map.entry("items", Set.of("id")),
        Map.entry("fluids", Set.of("id")),
        Map.entry("tank", Set.of("id", "capacity", "role", "fluid")),
        Map.entry("tanks", Set.of("id", "count", "capacity", "role", "fluid")),
        Map.entry("energy",
            Set.of("id", "capacity", "input-rate", "output-rate")),
        Map.entry("slots", Set.of("id", "count", "role", "limit")),
        Map.entry("slot", Set.of("id", "role", "limit")),
        Map.entry("groups", Set.of()),
        Map.entry("group", Set.of("id", "storage", "members", "mode", "color")),
        Map.entry("recipe-type", Set.of("id", "declare", "name")),
        Map.entry("processor",
            Set.of("id", "name", "recipe-type", "filter-inputs",
                "running-sound", "finish-sound")),
        Map.entry("redstone", Set.of("enabled", "pause")),
        Map.entry("recovery", Set.of("policy")));
    private static final Map<String, Set<String>> CHILDREN = Map.of("machine",
        Set.of("block", "appearance", "storage", "groups", "recipe-type",
            "processor", "redstone", "recovery", "screen"),
        "storage", Set.of("energy", "items", "fluids"), "fluids",
        Set.of("tank", "tanks"), "items", Set.of("slot", "slots"), "groups",
        Set.of("group"), "recipe-type", Set.of("generator"));

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
            case "appearance" -> List.of("front", "side", "top", "bottom");
            case "recipe-type" -> List.of("id");
            case "processor" ->
                List.of("recipe-type", "running-sound", "finish-sound");
            case "tank", "tanks" -> List.of("fluid");
            default -> List.<String>of();
        };
        for (var attribute : resources)
            if (node.attributes().containsKey(attribute))
                resource(node, attribute);
        if (node.name().equals("block")
            && node.attributes().containsKey("hardness"))
            hardness(node);
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
        requireChoice(block, "facing", "horizontal");
        requireChoice(block, "drop", "self");
        var appearance = one(root, "appearance");
        requireChoice(appearance, "preset", "cube-front");
        var textures = new HashMap<String, String>();
        for (var face : List.of("front", "side", "top"))
            textures.put(face, resource(appearance, face).toString());
        textures.put("bottom",
            appearance.attributes().containsKey("bottom")
                ? resource(appearance, "bottom").toString()
                : textures.get("side"));
        var storage = one(root, "storage");
        var power = optional(storage, "energy");
        var items = one(storage, "items");
        if (required(items, "id").equals("energy")
            || MachineStorageKeys.RESERVED.contains(required(items, "id")))
            throw error(items, "Reserved storage ID " + required(items, "id"));
        String powerId = power == null ? null : required(power, "id");
        // BasePoweredMachine owns this stable save key.
        if (powerId != null && !powerId.equals("energy"))
            throw error(power, "Powered machines require energy id='energy'");
        int energyColor = 0xd85b5b;
        var fluidNode = optional(storage, "fluids");
        var tanks = new ArrayList<Tank>();
        var fluidGroups = new LinkedHashMap<String, Group>();
        var tankIds = new HashMap<String, Integer>();
        if (fluidNode != null) {
            String fluidId = required(fluidNode, "id");
            if (fluidId.equals("energy")
                || fluidId.equals(required(items, "id"))
                || MachineStorageKeys.RESERVED.contains(fluidId))
                throw error(fluidNode,
                    "Duplicate or reserved storage ID " + fluidId);
            for (var node : fluidNode.children()) {
                String tankId = required(node, "id");
                Role role = role(node);
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
                    tanks.add(new Tank(member, role, capacity, filter));
                }
                if (fluidGroups.putIfAbsent(tankId,
                    new Group(tankId, members, mode(role), 0xf0a13b)) != null)
                    throw error(node, "Duplicate fluid group " + tankId);
            }
            if (tanks.isEmpty() || tanks.size() > 16)
                throw error(fluidNode, "Expected 1..16 tanks");
        }
        var slots = new ArrayList<Slot>();
        var groups = new LinkedHashMap<String, Group>();
        var ids = new HashMap<String, Integer>();
        for (var node : items.children()) {
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
                : 1;
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
                slots.add(new Slot(memberId, role, limit));
            }
            if (node.name().equals("slots")) {
                IoMode mode = switch (role) {
                    case INPUT -> IoMode.INPUT;
                    case OUTPUT -> IoMode.OUTPUT;
                    case STORAGE -> IoMode.BOTH;
                    case MANUAL -> IoMode.DISABLED;
                };
                if (groups.putIfAbsent(slotId, new Group(slotId, members, mode,
                    role == Role.INPUT ? 0x568bd8 : 0xe48f45)) != null)
                    throw error(node, "Duplicate group " + slotId);
            }
        }
        if (slots.isEmpty() || slots.size() > 54)
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
                        mode, color(node, prior.color())));
                    continue;
                }
                if (!required(node, "storage").equals(required(items, "id")))
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
                groups.put(groupId, new Group(groupId, members, mode,
                    color(node, prior == null ? 0x568bd8 : prior.color())));
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
        var recipe = one(root, "recipe-type");
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
        var processor = one(root, "processor");
        if (!recipeId.equals(resource(processor, "recipe-type")))
            throw error(processor, "Unknown recipe type");
        // All input groups are linked automatically; an explicit list must
        // agree.
        var inputs = java.util.stream.Stream
            .concat(groups.values().stream(), fluidGroups.values().stream())
            .filter(g -> g.mode() == IoMode.INPUT).map(Group::id).toList();
        if (processor.attributes().containsKey("filter-inputs")
            && !new HashSet<>(Arrays
                .asList(required(processor, "filter-inputs").split("\\s+")))
                .equals(new HashSet<>(inputs)))
            throw error(processor, "filter-inputs must name the input groups");
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
        return new MachineDefinition(id, checksum(root), required(root, "name"),
            hardness, resource(block, "mineable"),
            bool(block, "requires-tool", false), textures,
            power == null
                ? null
                : new Power(powerId,
                    number(power, "capacity", 1, Long.MAX_VALUE),
                    number(power, "input-rate", 0, Long.MAX_VALUE),
                    number(power, "output-rate", 0, Long.MAX_VALUE),
                    energyColor),
            fluidNode == null
                ? null
                : new Fluids(required(fluidNode, "id"), tanks,
                    List.copyOf(fluidGroups.values())),
            required(items, "id"), slots, List.copyOf(groups.values()),
            recipeType, required(processor, "id"),
            processor.attributes().getOrDefault("name", required(root, "name")),
            redstone != null && bool(redstone, "enabled", false),
            new Node("screen", attrs, screen.children(), screen.source(),
                screen.line()),
            new MachineDefinition.Sounds(
                optionalResource(processor, "running-sound"),
                optionalResource(processor, "finish-sound")));
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

    private static void requireChoice(Node node, String name, String value) {
        if (!required(node, name).equals(value))
            throw error(node, "Supported " + name + ": " + value);
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

    private static int color(Node node, int fallback) {
        var value = node.attributes().get("color");
        if (value == null)
            return fallback;
        if (!value.matches("#[0-9a-fA-F]{6}"))
            throw error(node, "color must be #RRGGBB");
        return Integer.parseInt(value.substring(1), 16);
    }

    public static ScreenException error(Node node, String message) {
        return new ScreenException(node.source(), node.line(), message);
    }
}
