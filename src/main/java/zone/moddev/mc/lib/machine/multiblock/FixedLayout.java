package zone.moddev.mc.lib.machine.multiblock;

import java.util.List;
import java.util.Map;

/**
 * Fixed candidate producer. Validation and placement are separate from
 * resolving the layout.
 */
public record FixedLayout(ResolvedStructure structure)
    implements StructureResolver {
    public FixedLayout(List<StructureMember> members,
        List<StructureRequirement> requirements) {
        this(new ResolvedStructure(members, requirements, Map.of()));
    }
    @Override
    public Resolution resolve(WorldView world, StructureTransform transform) {
        return Resolution.found(structure);
    }
    @Override
    public boolean accepts(ResolvedStructure candidate) {
        return structure.equals(candidate);
    }
}
