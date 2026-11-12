package zone.moddev.mc.lib.machine.neoforge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

import zone.moddev.mc.lib.machine.multiblock.LinkedMember;
import zone.moddev.mc.lib.machine.multiblock.MultiblockController;
import zone.moddev.mc.lib.machine.multiblock.MultiblockDefinition;
import zone.moddev.mc.lib.machine.multiblock.MultiblockInstance;
import zone.moddev.mc.lib.machine.multiblock.StructureResolver;
import zone.moddev.mc.lib.machine.multiblock.StructureTransform;

/**
 * Server placement transaction. It does not consume items or own gameplay
 * resources.
 */
public final class MultiblockPlacement {
    private record Attempt(net.minecraft.world.level.Level level,
        java.util.Set<BlockPos> positions) {
    }
    private static final ThreadLocal<Attempt> WRITING = new ThreadLocal<>();
    private MultiblockPlacement() {
    }
    public static boolean writing() {
        return WRITING.get() != null;
    }
    public static boolean writing(net.minecraft.world.level.Level level,
        BlockPos pos) {
        var attempt = WRITING.get();
        return attempt != null && attempt.level() == level
            && attempt.positions().contains(pos);
    }
    public record Result(boolean success, String diagnostic) {
        static Result refused(String reason) {
            return new Result(false, reason);
        }
    }

    public static Result place(BlockPlaceContext context,
        MultiblockDefinition definition) {
        var level = context.getLevel();
        var player = context.getPlayer();
        if (level.isClientSide || writing() || player == null)
            return Result.refused("server_player_required");
        var transform = new StructureTransform(context.getClickedPos(),
            context.getHorizontalDirection().getOpposite());
        var result = definition.resolver()
            .resolve(new StructureResolver.WorldView() {
                @Override
                public boolean loaded(BlockPos pos) {
                    return level.hasChunkAt(pos);
                }
                @Override
                public BlockState state(BlockPos pos) {
                    if (!loaded(pos))
                        throw new IllegalStateException(
                            "Resolver read an unloaded chunk");
                    return level.getBlockState(pos);
                }
            }, transform);
        if (result.structure() == null)
            return Result.refused(result.diagnostic());
        var candidate = result.structure();
        definition.validate(candidate);
        var previous = new LinkedHashMap<BlockPos, BlockState>();
        var placed = new LinkedHashMap<BlockPos, BlockState>();
        for (var member : candidate.members()) {
            var pos = transform.position(member.offset());
            if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)
                || !level.getWorldBorder().isWithinBounds(pos))
                return Result.refused("unavailable_position:" + member.key());
            var old = level.getBlockState(pos);
            var state = member.state().rotate(transform.rotation());
            if (level.getBlockEntity(pos) != null
                || MultiblockMembers.INSTANCE.link(level, pos) != null
                || !old.canBeReplaced(BlockPlaceContext.at(context, pos,
                    context.getClickedFace()))
                || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, context.getClickedFace(),
                    context.getItemInHand())
                || !level.isUnobstructed(state, pos,
                    net.minecraft.world.phys.shapes.CollisionContext.empty()))
                return Result.refused("blocked_position:" + member.key());
            previous.put(pos, old);
            placed.put(pos, state);
        }
        for (var requirement : candidate.requirements()) {
            var pos = transform.position(requirement.offset());
            if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)
                || !level.getWorldBorder().isWithinBounds(pos)
                || !definition.rules().get(requirement.rule()).test(level, pos))
                return Result.refused("requirement:" + requirement.rule());
        }
        var ordered = new ArrayList<>(candidate.members());
        ordered.sort(java.util.Comparator
            .comparing(member -> !member.role().equals("controller")));
        var snapshots = new ArrayList<BlockSnapshot>();
        var written = new LinkedHashMap<BlockPos, BlockEntity>();
        MultiblockInstance instance = null;
        boolean success = false;
        boolean capture = level.captureBlockSnapshots;
        WRITING.set(new Attempt(level, java.util.Set.copyOf(placed.keySet())));
        level.captureBlockSnapshots = false;
        try {
            for (var member : ordered) {
                var pos = transform.position(member.offset());
                if (!level.getBlockState(pos).equals(previous.get(pos)))
                    return Result.refused("world_changed");
                snapshots
                    .add(BlockSnapshot.create(level.dimension(), level, pos));
                if (!level.setBlock(pos, placed.get(pos), 2))
                    return Result.refused("write_failed");
                var entity = level.getBlockEntity(pos);
                written.put(pos, entity);
                if (member.role().equals("controller")) {
                    if (!(entity instanceof MultiblockController controller)
                        || controller.multiblock() == null)
                        return Result.refused("controller_missing");
                    instance = controller.multiblock();
                    if (!instance.definition().id().equals(definition.id())
                        || instance.definition().version() != definition
                            .version()
                        || !instance.stage(candidate, transform.facing()))
                        return Result.refused("controller_definition_mismatch");
                } else {
                    if (!(entity instanceof LinkedMember part))
                        return Result.refused("part_missing");
                    part.memberLink(instance.memberLink(member.key()));
                }
            }
            if (EventHooks.onMultiBlockPlace(player, snapshots,
                context.getClickedFace()))
                return Result.refused("placement_denied");
            for (var entry : placed.entrySet()) {
                if (level.getBlockEntity(entry.getKey()) != written
                    .get(entry.getKey())
                    || !level.getBlockState(entry.getKey())
                        .equals(entry.getValue())
                    || !entry.getValue().canSurvive(level, entry.getKey()))
                    return Result.refused("world_changed");
            }
            if (instance == null || !instance.commit())
                return Result.refused("formation_failed");
            success = true;
            // Keep all notifications inside our capture boundary; the outer
            // ItemStack use hook
            // must not post a second placement event for changes caused by
            // these notifications.
            for (var entry : placed.entrySet())
                level.updateNeighborsAt(entry.getKey(),
                    entry.getValue().getBlock());
        } finally {
            if (!success) {
                if (instance != null && !instance.committed())
                    instance.discard();
                var reverse = new ArrayList<>(written.entrySet());
                java.util.Collections.reverse(reverse);
                boolean restoring = level.restoringBlockSnapshots;
                level.restoringBlockSnapshots = true;
                try {
                    for (var entry : reverse) {
                        var pos = entry.getKey();
                        if (level.getBlockEntity(pos) == entry.getValue()
                            && level.getBlockState(pos).equals(placed.get(pos)))
                            level.setBlock(pos, previous.get(pos), 3);
                    }
                } finally {
                    level.restoringBlockSnapshots = restoring;
                }
            }
            level.captureBlockSnapshots = capture;
            WRITING.remove();
        }
        return new Result(true, "formed");
    }
}
