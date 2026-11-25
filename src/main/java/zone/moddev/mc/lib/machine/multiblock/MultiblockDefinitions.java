package zone.moddev.mc.lib.machine.multiblock;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.gui.load.ScreenException;
import zone.moddev.mc.lib.gui.load.ScreenReader.Node;
import zone.moddev.mc.lib.gui.load.ScreenReader;

/** Loads fixed multiblock definitions for Java-registered controllers. */
public final class MultiblockDefinitions {
    public static final String NAMESPACE = "https://moddev.zone/mmdlib/multiblock";

    private MultiblockDefinitions() {
    }

    /**
     * Loads a bundled XML definition after the member blocks are registered.
     */
    public static MultiblockDefinition load(ResourceLocation id,
        Class<?> owner) {
        String path = "/data/" + id.getNamespace() + "/mmdlib/multiblock/"
            + id.getPath() + ".xml";
        try (var input = owner.getResourceAsStream(path)) {
            if (input == null)
                throw new IllegalArgumentException(
                    "Missing multiblock " + path);
            return read(id, path, input, BuiltInRegistries.BLOCK.asLookup());
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read multiblock " + path,
                ex);
        }
    }

    /**
     * Reads one fixed layout using the supplied lookup to resolve block states.
     */
    public static MultiblockDefinition read(ResourceLocation id, String source,
        InputStream input, HolderLookup<Block> blocks) throws IOException {
        var root = ScreenReader.parseMachine(source, input);
        if (!root.name().equals("structure")
            || !root.namespace().equals(NAMESPACE))
            throw error(root, "Expected <structure> in " + NAMESPACE);
        attributes(root,
            Set.of("version", "max-members", "max-extent", "dismantling"));

        var members = new ArrayList<StructureMember>();
        var requirements = new ArrayList<StructureRequirement>();
        var ports = new ArrayList<StructurePort>();
        var rules = new HashMap<String, MultiblockDefinition.RequirementRule>();
        for (var child : root.children()) {
            if (!child.namespace().equals(NAMESPACE)
                || !child.children().isEmpty())
                throw error(child, "Expected an empty multiblock entry");
            try {
                switch (child.name()) {
                    case "member" -> {
                        attributes(child,
                            Set.of("id", "role", "offset", "state"));
                        members.add(new StructureMember(required(child, "id"),
                            required(child, "role"), offset(child),
                            state(child, blocks)));
                    }
                    case "rule" -> {
                        attributes(child, Set.of("id", "type"));
                        String key = required(child, "id");
                        if (!required(child, "type").equals("air"))
                            throw error(child,
                                "Unknown rule type " + required(child, "type"));
                        if (rules.putIfAbsent(key, (level, pos) -> level
                            .getBlockState(pos).isAir()) != null)
                            throw error(child, "Duplicate rule " + key);
                    }
                    case "requirement" -> {
                        attributes(child,
                            Set.of("rule", "offset", "continuous"));
                        requirements.add(
                            new StructureRequirement(required(child, "rule"),
                                offset(child), bool(child, "continuous")));
                    }
                    case "port" -> ports.add(port(child));
                    default -> throw error(child,
                        "Unknown entry <" + child.name() + ">");
                }
            } catch (ScreenException ex) {
                throw ex;
            } catch (IllegalArgumentException | CommandSyntaxException ex) {
                throw new ScreenException(child.source(), child.line(),
                    ex.getMessage(), ex);
            }
        }
        try {
            var layout = new FixedLayout(members, requirements);
            var definition = new MultiblockDefinition(id,
                number(root, "version"), layout, number(root, "max-members"),
                number(root, "max-extent"), enumValue(root, "dismantling",
                    MultiblockDefinition.Dismantling.class),
                rules, ports);
            definition.validate(layout.structure());
            return definition;
        } catch (ScreenException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new ScreenException(root.source(), root.line(),
                ex.getMessage(), ex);
        }
    }

    private static BlockState state(Node node, HolderLookup<Block> blocks)
        throws CommandSyntaxException {
        var reader = new StringReader(required(node, "state"));
        var result = BlockStateParser.parseForBlock(blocks, reader, false);
        if (reader.canRead())
            throw error(node, "Unexpected content after block state");
        return result.blockState();
    }

    private static StructurePort port(Node node) {
        attributes(node, Set.of("member", "resource", "kind", "faces",
            "unsided", "input", "output"));
        var faces = EnumSet.noneOf(Direction.class);
        for (String value : node.attributes().getOrDefault("faces", "").trim()
            .split("\\s+")) {
            if (value.isEmpty())
                continue;
            var face = RelativeFace.valueOf(value.toUpperCase(Locale.ROOT))
                .toWorld(Direction.NORTH);
            if (!faces.add(face))
                throw error(node, "Duplicate port face " + value);
        }
        return new StructurePort(required(node, "member"),
            required(node, "resource"),
            enumValue(node, "kind", StructurePort.Kind.class), faces,
            bool(node, "unsided"), bool(node, "input"), bool(node, "output"));
    }

    private static BlockPos offset(Node node) {
        String[] axes = required(node, "offset").split("\\s+");
        if (axes.length != 3)
            throw error(node, "Expected three integers for offset");
        return new BlockPos(Integer.parseInt(axes[0]),
            Integer.parseInt(axes[1]), Integer.parseInt(axes[2]));
    }

    private static int number(Node node, String attribute) {
        return Integer.parseInt(required(node, attribute));
    }

    private static <E extends Enum<E>> E enumValue(Node node, String attribute,
        Class<E> type) {
        return Enum.valueOf(type, required(node, attribute).replace('-', '_')
            .toUpperCase(Locale.ROOT));
    }

    private static boolean bool(Node node, String attribute) {
        return switch (node.attributes().getOrDefault(attribute, "false")) {
            case "true" -> true;
            case "false" -> false;
            default ->
                throw error(node, "Expected true or false for " + attribute);
        };
    }

    private static String required(Node node, String attribute) {
        String value = node.attributes().get(attribute);
        if (value == null || value.isBlank())
            throw error(node, "Missing " + attribute);
        return value.trim();
    }

    private static void attributes(Node node, Set<String> allowed) {
        for (String attribute : node.attributes().keySet())
            if (!allowed.contains(attribute))
                throw error(node, "Unknown attribute " + attribute);
    }

    private static ScreenException error(Node node, String message) {
        return new ScreenException(node.source(), node.line(), message);
    }
}
