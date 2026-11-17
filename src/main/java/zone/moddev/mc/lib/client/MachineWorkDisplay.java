package zone.moddev.mc.lib.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.joml.Vector3f;

/**
 * Lightweight particles on active target blocks, independent of any open
 * screen.
 */
public final class MachineWorkDisplay {
    private MachineWorkDisplay() {
    }

    /**
     * Call once per client machine tick with a synchronized target snapshot.
     */
    public static void tick(BlockEntity machine, List<BlockPos> targets,
        int color) {
        var client = Minecraft.getInstance();
        var level = client.level;
        if (level == null || level != machine.getLevel() || machine.isRemoved()
            || client.isPaused() || targets.isEmpty())
            return;
        long tick = level.getGameTime();
        // Spread machines over two ticks and bound the denser column effect.
        if (Math.floorMod(tick + machine.getBlockPos().asLong(), 2) != 0)
            return;
        var camera = client.gameRenderer.getMainCamera().getPosition();
        var particle = new DustParticleOptions(
            new Vector3f(((color >> 16) & 255) / 255f,
                ((color >> 8) & 255) / 255f, (color & 255) / 255f),
            .35f);
        int levels = switch (client.options.particles().get()) {
            case ALL -> 9;
            case DECREASED -> 5;
            case MINIMAL -> 3;
        };
        int count = Math.min(targets.size(), 4);
        int start = (int) Math.floorMod(tick / 2 * 4, targets.size());
        for (int i = 0; i < count; i++) {
            var target = targets.get((start + i) % targets.size());
            if (!level.hasChunkAt(target)
                || target.distToCenterSqr(camera) > 64 * 64)
                continue;
            var shape = level.getBlockState(target).getShape(level, target);
            double baseY = target.getY() + .05
                + (shape.isEmpty()
                    ? 0
                    : shape.max(net.minecraft.core.Direction.Axis.Y));
            // Two opposing helices rotate around the block center. Short-lived
            // dust leaves a visible trail without filling in a static cylinder.
            for (int sample = 0; sample < levels; sample++) {
                double progress = sample / (double) (levels - 1);
                double angle = tick * .22 + progress * Math.PI * 2;
                for (int strand = 0; strand < 2; strand++) {
                    double phase = angle + strand * Math.PI;
                    var spark = client.particleEngine.createParticle(particle,
                        target.getX() + .5 + Math.cos(phase) * .20,
                        baseY + progress * 1.5,
                        target.getZ() + .5 + Math.sin(phase) * .20, 0, 0, 0);
                    if (spark != null)
                        spark.setLifetime(6);
                }
            }
        }
    }
}
