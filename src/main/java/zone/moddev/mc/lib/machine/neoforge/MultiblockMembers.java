package zone.moddev.mc.lib.machine.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import zone.moddev.mc.lib.machine.multiblock.LinkedMember;
import zone.moddev.mc.lib.machine.multiblock.MemberLink;
import zone.moddev.mc.lib.machine.multiblock.MultiblockController;
import zone.moddev.mc.lib.machine.multiblock.MultiblockInstance;
import zone.moddev.mc.lib.machine.multiblock.StructureMembership;

/** Linked-entity membership adapter; never requests an unloaded chunk. */
public enum MultiblockMembers implements StructureMembership {
    INSTANCE;

    @Override
    public MemberLink link(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos))
            return null;
        var entity = level.getBlockEntity(pos);
        if (entity instanceof MultiblockController controller && controller.multiblock() != null)
            return controller.multiblock().controllerLink();
        return entity instanceof LinkedMember member ? member.memberLink() : null;
    }

    @Override
    public boolean claim(Level level, BlockPos pos, MemberLink link) {
        if (!level.hasChunkAt(pos))
            return false;
        var previous = link(level, pos);
        if (previous != null && !previous.equals(link))
            return false;
        var entity = level.getBlockEntity(pos);
        if (entity instanceof LinkedMember member) {
            member.memberLink(link);
            return true;
        }
        return entity instanceof MultiblockController && link.equals(previous);
    }

    @Override
    public void release(Level level, BlockPos pos, MemberLink expected) {
        if (expected.equals(link(level, pos))
            && level.getBlockEntity(pos) instanceof LinkedMember member)
            member.memberLink(null);
    }

    public static MultiblockInstance resolve(Level level, BlockPos pos) {
        var link = INSTANCE.link(level, pos);
        if (link == null || !link.dimension().equals(level.dimension().location())
            || !level.hasChunkAt(link.controller()))
            return null;
        var entity = level.getBlockEntity(link.controller());
        if (!(entity instanceof MultiblockController controller) || controller.multiblock() == null)
            return null;
        var instance = controller.multiblock();
        return instance.owns(pos, link) ? instance : null;
    }

    @Override
    public void availabilityChanged(Level level, BlockPos pos) {
        if (level.hasChunkAt(pos))
            level.invalidateCapabilities(pos);
    }

    /** Reconciles orphaned parts on load; unavailable or incompatible owners are left intact. */
    public static void reconcile(Level level, BlockPos pos) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof LinkedMember part))
            return;
        var link = part.memberLink();
        if (link == null)
            return;
        if (!link.dimension().equals(level.dimension().location()))
            return;
        if (!level.hasChunkAt(link.controller())) {
            level.scheduleTick(pos, level.getBlockState(pos).getBlock(), 20);
            return;
        }
        var entity = level.getBlockEntity(link.controller());
        if (entity instanceof MultiblockController controller && controller.multiblock() != null) {
            var owner = controller.multiblock();
            if (owner.formation() == null || owner.changing())
                return;
            if (owner.owns(pos, link))
                return;
        }
        // Only the still-linked part can remove itself; no machine item is awarded here.
        if (link.equals(INSTANCE.link(level, pos)))
            level.removeBlock(pos, false);
    }
}
