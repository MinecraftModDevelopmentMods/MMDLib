package zone.moddev.mc.lib.machine.multiblock;

import net.minecraft.core.BlockPos;

/** A named definition-owned rule; required space is never owned or removed. */
public record StructureRequirement(String rule, BlockPos offset, boolean continuous) {
    public StructureRequirement {
        if (rule.isBlank() || rule.length() > 128)
            throw new IllegalArgumentException("Invalid rule");
        offset = offset.immutable();
    }
}
