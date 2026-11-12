package zone.moddev.mc.lib.machine.multiblock;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Consumer-owned Java definition. No global registry or assembly-mode-specific catalog. */
public record MultiblockDefinition(ResourceLocation id, int version, StructureResolver resolver,
    int maxMembers, int maxExtent, Dismantling dismantling,
    Map<String, RequirementRule> rules, List<StructurePort> ports) {
    public enum Dismantling {
        REMOVE_OWNED, LEAVE_BLOCKS
    }
    @FunctionalInterface
    public interface RequirementRule {
        boolean test(Level level, BlockPos pos);
    }
    public MultiblockDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(resolver);
        Objects.requireNonNull(dismantling);
        if (version < 1 || maxMembers < 1 || maxMembers > 4096 || maxExtent < 1 || maxExtent > 128)
            throw new IllegalArgumentException("Invalid definition limits");
        rules = Map.copyOf(rules);
        ports = List.copyOf(ports);
        if (ports.size() > 256)
            throw new IllegalArgumentException("Too many ports");
    }
    public void validate(ResolvedStructure structure) {
        if (structure.members().size() > maxMembers || !resolver.accepts(structure))
            throw new IllegalArgumentException("Incompatible structure manifest");
        for (var member : structure.members())
            checkOffset(member.offset());
        for (var requirement : structure.requirements()) {
            checkOffset(requirement.offset());
            if (!rules.containsKey(requirement.rule()))
                throw new IllegalArgumentException("Unknown requirement");
        }
        var bindings = new java.util.HashSet<String>();
        for (var port : ports) {
            structure.member(port.member());
            for (var face : DirectionKeys.of(port))
                if (!bindings.add(port.member() + "/" + port.kind() + "/" + face))
                    throw new IllegalArgumentException("Ambiguous port face");
        }
    }
    private void checkOffset(BlockPos pos) {
        if (Math.abs((long) pos.getX()) > maxExtent || Math.abs((long) pos.getY()) > maxExtent
            || Math.abs((long) pos.getZ()) > maxExtent)
            throw new IllegalArgumentException("Structure extent exceeded");
    }
    private static final class DirectionKeys {
        static List<String> of(StructurePort port) {
            var keys = new java.util.ArrayList<String>();
            port.faces().forEach(face -> keys.add(face.getName()));
            if (port.unsided())
                keys.add("unsided");
            return keys;
        }
    }
}
