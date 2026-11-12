package zone.moddev.mc.lib.machine.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only discovery seam. Phase 2 resolvers may discover bounded, variable-size membership. */
public interface StructureResolver {
    Resolution resolve(WorldView world, StructureTransform transform);

    /** Reject incompatible saved manifests without rewriting the surrounding world. */
    boolean accepts(ResolvedStructure structure);

    interface WorldView {
        boolean loaded(BlockPos pos);
        BlockState state(BlockPos pos);
    }

    record Resolution(ResolvedStructure structure, String diagnostic) {
        public Resolution {
            if (diagnostic == null || diagnostic.length() > 512)
                throw new IllegalArgumentException("Invalid diagnostic");
        }
        public static Resolution found(ResolvedStructure structure) {
            return new Resolution(java.util.Objects.requireNonNull(structure), "");
        }
        public static Resolution refused(String diagnostic) {
            return new Resolution(null, diagnostic);
        }
    }
}
