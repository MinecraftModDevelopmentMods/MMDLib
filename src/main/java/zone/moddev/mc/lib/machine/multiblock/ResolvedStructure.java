package zone.moddev.mc.lib.machine.multiblock;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;

/**
 * Actual membership, independent of placement and capable of representing
 * discovered shapes.
 */
public record ResolvedStructure(List<StructureMember> members,
    List<StructureRequirement> requirements,
    Map<String, Integer> measurements) {
    public ResolvedStructure {
        members = List.copyOf(members);
        requirements = List.copyOf(requirements);
        measurements = Map.copyOf(measurements);
        if (members.isEmpty() || members.size() > 4096
            || requirements.size() > 4096 || measurements.size() > 64)
            throw new IllegalArgumentException("Structure limits exceeded");
        var keys = new HashSet<String>();
        var positions = new HashSet<BlockPos>();
        int controllers = 0;
        for (var member : members) {
            if (!keys.add(member.key()) || !positions.add(member.offset()))
                throw new IllegalArgumentException(
                    "Duplicate member key/position");
            if (member.role().equals("controller")) {
                controllers++;
                if (!member.offset().equals(BlockPos.ZERO))
                    throw new IllegalArgumentException(
                        "Controller must be at origin");
            }
        }
        if (controllers != 1)
            throw new IllegalArgumentException(
                "Exactly one controller required");
        measurements.forEach((key, value) -> {
            if (key.isBlank() || key.length() > 128 || value < 0)
                throw new IllegalArgumentException("Invalid measurement");
        });
    }

    public StructureMember member(String key) {
        return members.stream().filter(member -> member.key().equals(key))
            .findFirst().orElseThrow();
    }
}
