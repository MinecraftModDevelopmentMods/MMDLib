package zone.moddev.mc.lib.gui.client;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Connection-local overlays, retained after the machine screen is closed. */
@EventBusSubscriber(modid = "mmdlib", value = Dist.CLIENT)
public final class WorkingAreaDisplay {
    private static final Map<Key, Entry> AREAS = new LinkedHashMap<>();
    private static ClientLevel level;
    private record Key(BlockPos origin, String processor) {
        static Key of(CompoundTag data) {
            return new Key(BlockPos.of(data.getLong("origin")),
                data.getString("processor"));
        }
    }
    private record Entry(BlockEntity owner, AABB bounds, int color) {
    }

    private WorkingAreaDisplay() {
    }

    @SubscribeEvent
    public static void disconnected(
        net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        AREAS.clear();
        level = null;
    }

    private static void refresh() {
        var current = Minecraft.getInstance().level;
        if (level != current) {
            AREAS.clear();
            level = current;
        }
        if (level != null)
            AREAS.entrySet()
                .removeIf(entry -> entry.getValue().owner().isRemoved()
                    || !level.hasChunkAt(entry.getKey().origin())
                    || level.getBlockEntity(entry.getKey().origin()) != entry
                        .getValue().owner());
    }

    private static boolean valid(CompoundTag data) {
        return level != null && data.contains("origin")
            && !data.getString("processor").isEmpty() && level.dimension()
                .location().toString().equals(data.getString("dimension"));
    }

    public static boolean visible(CompoundTag data) {
        refresh();
        return valid(data) && data.getBoolean("available")
            && AREAS.containsKey(Key.of(data));
    }

    public static void update(CompoundTag data) {
        refresh();
        if (!valid(data))
            return;
        var key = Key.of(data);
        if (!data.getBoolean("available")) {
            AREAS.remove(key);
            return;
        }
        AREAS.computeIfPresent(key,
            (ignored, previous) -> new Entry(previous.owner(), bounds(data),
                color(data)));
    }

    public static void toggle(CompoundTag data) {
        refresh();
        if (!valid(data))
            return;
        var key = Key.of(data);
        if (AREAS.remove(key) != null || !data.getBoolean("available"))
            return;
        var pos = key.origin();
        if (!level.hasChunkAt(pos))
            return;
        var owner = level.getBlockEntity(pos);
        if (owner == null)
            return;
        if (AREAS.size() >= 32)
            AREAS.remove(AREAS.keySet().iterator().next());
        AREAS.put(key, new Entry(owner, bounds(data), color(data)));
    }

    public static int color(CompoundTag data) {
        return data.contains("color")
            ? data.getInt("color") & 0xffffff
            : zone.moddev.mc.lib.machine.presentation.VisualizerStyle.DEFAULT
                .rangeColor();
    }

    private static AABB bounds(CompoundTag data) {
        return new AABB(data.getDouble("minX"), data.getDouble("minY"),
            data.getDouble("minZ"), data.getDouble("maxX"),
            data.getDouble("maxY"), data.getDouble("maxZ"));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event
            .getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        refresh();
        if (AREAS.isEmpty())
            return;
        var camera = event.getCamera().getPosition();
        var pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var lines = buffers.getBuffer(RenderType.lines());
        for (var entry : AREAS.values()) {
            if (entry.owner().getBlockPos().distToCenterSqr(camera) > 128 * 128)
                continue;
            LevelRenderer.renderLineBox(pose, lines,
                entry.bounds().inflate(.002),
                ((entry.color() >> 16) & 255) / 255f,
                ((entry.color() >> 8) & 255) / 255f,
                (entry.color() & 255) / 255f, .85f);
        }
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }
}
