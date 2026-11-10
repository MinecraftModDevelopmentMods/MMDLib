package zone.moddev.mc.lib.gui.model.elements;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Common;

/**
 * A sequence of GUI-atlas sprites, timed in game ticks (including partial
 * ticks).
 */
public class AnimatedSpriteElement extends SpriteElement {
    private final List<ResourceLocation> frames;
    private final int ticksPerFrame;
    private final boolean loop;

    public AnimatedSpriteElement(Common common, List<ResourceLocation> frames,
        int width, int height, int tint, int ticksPerFrame, boolean loop) {
        super(common, first(frames), width, height, tint);
        this.frames = List.copyOf(frames);
        if (ticksPerFrame <= 0)
            throw new IllegalArgumentException(
                "ticks-per-frame must be positive");
        this.ticksPerFrame = ticksPerFrame;
        this.loop = loop;
    }

    private static ResourceLocation first(List<ResourceLocation> frames) {
        if (frames.isEmpty())
            throw new IllegalArgumentException(
                "Animation requires at least one frame");
        return frames.getFirst();
    }

    public List<ResourceLocation> frames() {
        return frames;
    }

    public int ticksPerFrame() {
        return ticksPerFrame;
    }

    public boolean loop() {
        return loop;
    }

    public ResourceLocation frameAt(double ticks) {
        long frame = (long) Math.floor(Math.max(0, ticks) / ticksPerFrame);
        return frames.get((int) (loop
            ? frame % frames.size()
            : Math.min(frame, frames.size() - 1)));
    }

    @Override
    public List<Layer> layers(Rect bounds, double ticks, double progress) {
        return List.of(new Layer(frameAt(ticks), tint(),
            new Rect(0, 0, bounds.width(), bounds.height()), 0, bounds.width(),
            bounds.height()));
    }

    @Override
    public String toString() {
        return super.toString() + "[" + frames + "," + ticksPerFrame + ","
            + loop + "]";
    }
}
