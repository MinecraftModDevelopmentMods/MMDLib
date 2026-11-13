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
        // A new preset replaces the previous preset's base configuration, but
        // retains parts.
        if (child.name().equals("appearance")
            && child.attributes().containsKey("preset") && !child.attributes()
                .get("preset").equals(base.attributes().get("preset")))
            for (var attribute : List.of("front", "side", "top", "bottom",
                "model"))
                attrs.remove(attribute);
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
            Set.of("hardness", "facing", "drop", "mineable", "requires-tool",
                "stack-size", "no-occlusion", "immovable", "portable",
                "drawers")),
        Map.entry("appearance",
            Set.of("preset", "front", "side", "top", "bottom", "model")),
        Map.entry("part", Set.of("id", "model", "when")),
        Map.entry("face-texture",
            Set.of("id", "face", "texture", "when", "emissive-mask")),
        Map.entry("storage", Set.of()), Map.entry("items", Set.of("id")),
        Map.entry("fluids", Set.of("id")),
        Map.entry("tank",
            Set.of("id", "capacity", "role", "fluid", "color", "automation",
                "name")),
        Map.entry("tanks",
            Set.of("id", "count", "capacity", "role", "fluid", "color",
                "automation", "name")),
        Map.entry("energy",
            Set.of("id", "capacity", "input-rate", "output-rate", "color",
                "name")),
        Map.entry("slots",
            Set.of("id", "count", "role", "limit", "color", "type",
                "default-item", "name")),
        Map.entry("slot",
            Set.of("id", "count", "role", "limit", "type", "default-item",
                "name")),
        Map.entry("groups", Set.of()),
        Map.entry("group",
            Set.of("id", "storage", "members", "mode", "color", "name")),
        Map.entry("recipe-type", Set.of("id", "declare", "name")),
        Map.entry("processor",
            Set.of("id", "name", "recipe-type", "filter-inputs", "selectable",
                "default-recipe", "class")),
        Map.entry("redstone", Set.of("enabled", "pause")),
        Map.entry("shape", Set.of("id", "when", "bounds")),
        Map.entry("shapes", Set.of()), Map.entry("recovery", Set.of("policy")));
    private static final Map<String, Set<String>> CHILDREN = Map.of("machine",
        Set.of("block", "appearance", "storage", "groups", "recipe-type",
            "processor", "redstone", "recovery", "screen", "shapes"),
        "storage", Set.of("energy", "items", "fluids"), "fluids",
        Set.of("tank", "tanks"), "items", Set.of("slot", "slots"), "groups",
        Set.of("group"), "appearance", Set.of("part", "face-texture"),
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
            case "face-texture" -> List.of("texture", "emissive-mask");
            case "recipe-type" -> List.of("id");
            case "processor" -> List.of("recipe-type", "default-recipe");
            case "tank", "tanks" -> List.of("fluid");
            case "slot", "slots" -> List.of("default-item");
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
        requireChoice(block, "facing", "horizontal");
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
                    tanks.add(new Tank(member, role, capacity, filter));
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
                    color(node, defaultItemColor(groups, mode)), true,
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
                        true, node.attributes().getOrDefault("name",
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
        var recipe = optional(root, "recipe-type");
        var processor = optional(root, "processor");
        boolean custom = processor != null
            && processor.attributes().containsKey("class");
        if (custom && (recipe != null
            || processor.attributes().containsKey("recipe-type")
            || processor.attributes().containsKey("selectable")
            || processor.attributes().containsKey("filter-inputs")))
            throw error(processor,
                "Custom processors do not declare recipe processing options");
        if (!custom && (recipe == null) != (processor == null))
            throw error(root,
                "recipe-type and processor must be supplied together");
        if (processor == null) {
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
        var recipeType = recipe == null ? null : recipeType(recipe, loaders);
        if (!custom && processor != null
            && !recipeType.id().equals(resource(processor, "recipe-type")))
            throw error(processor, "Unknown recipe type");
        // All input groups are linked automatically when processing is enabled.
        var inputs = java.util.stream.Stream
            .concat(groups.values().stream(), fluidGroups.values().stream())
            .filter(g -> g.mode() == IoMode.INPUT).map(Group::id).toList();
        if (processor != null
            && processor.attributes().containsKey("filter-inputs")
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
        var modelParts = new ArrayList<ConditionalModelPart>();
        var faceTextures = new ArrayList<ConditionalFaceTexture>();
        if (appearance.children().size() > 64)
            throw error(appearance,
                "At most 64 conditional parts and face-texture rules are supported");
        for (var part : appearance.children()) {
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
                double[] v = Arrays.stream(bounds)
                    .mapToDouble(Double::parseDouble).toArray();
                shapes.add(new MachineBehavior.Shape(required(shape, "id"),
                    required(shape, "when"), v[0], v[1], v[2], v[3], v[4],
                    v[5]));
            }
        var behavior = new MachineBehavior(
            custom
                ? ProcessorDeclaration.load(required(processor, "class"),
                    loaders.get(processor.source()))
                : null,
            (int) (block.attributes().containsKey("stack-size")
                ? number(block, "stack-size", 1, 64)
                : 64),
            bool(block, "no-occlusion", false), bool(block, "immovable", false),
            bool(block, "portable", false), bool(block, "drawers", true),
            shapes);
        if (behavior.portable() && (!custom
            || !zone.moddev.mc.lib.machine.feature.PortableProcessor.class
                .isAssignableFrom(behavior.processor().type())))
            throw error(block, "Portable machines need a PortableProcessor");
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
                    energyColor, energyName),
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
            ghosts, behavior);
        var defaults = definition.hasConditionalAppearance()
            ? definition.visualDefaults()
            : Map.<String, Object>of();
        for (var part : appearance.children()) {
            try {
                VisualCondition.compile(required(part, "when"), defaults);
            } catch (IllegalArgumentException ex) {
                throw error(part, ex.getMessage());
            }
        }
        var processorDefaults = definition.visualDefaults().entrySet().stream()
            .filter(entry -> entry.getKey().startsWith("processors."))
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                Map.Entry::getValue));
        for (var shape : shapes)
            VisualCondition.compile(shape.when(), processorDefaults);
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

    private static int defaultItemColor(Map<String, Group> groups,
        IoMode mode) {
        int ordinal = (int) groups.values().stream()
            .filter(group -> group.mode() == mode).count();
        return MachineColors.item(mode, ordinal);
    }

    private static int color(Node node, int fallback) {
        var value = node.attributes().get("color");
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
