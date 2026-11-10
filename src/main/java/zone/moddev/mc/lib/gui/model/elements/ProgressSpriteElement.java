package zone.moddev.mc.lib.gui.model.elements;

import static zone.moddev.mc.lib.gui.model.ValueContract.NUMBERS;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.ProgressDirection;
import zone.moddev.mc.lib.gui.model.ValueContract;
import zone.moddev.mc.lib.gui.value.ValueType;

/** A background sprite with a foreground revealed in the configured direction. */
public class ProgressSpriteElement extends SpriteElement {
    private final ResourceLocation background;
    private final int backgroundTint;
    private final ProgressDirection direction;
    private final boolean rotate;

    public ProgressSpriteElement(
        Common common,
        ResourceLocation background,
        ResourceLocation foreground,
        int width,
        int height,
        ProgressDirection direction) {
        this(common, background, foreground, width, height, direction, 0xffffffff, 0xffffffff,
            false);
    }

    public ProgressSpriteElement(
        Common common,
        ResourceLocation background,
        ResourceLocation foreground,
        int width,
        int height,
        ProgressDirection direction,
        int tint,
        int backgroundTint,
        boolean rotate) {
        super(common, foreground, width, height, tint);
        this.background = Objects.requireNonNull(background);
        this.direction = Objects.requireNonNull(direction);
        this.backgroundTint = backgroundTint;
        this.rotate = rotate;
    }

    public ResourceLocation background() {
        return background;
    }

    public int backgroundTint() {
        return backgroundTint;
    }

    public ProgressDirection direction() {
        return direction;
    }

    public boolean rotate() {
        return rotate;
    }

    public Rect fill(int width, int height, double progress) {
        double ratio = Double.isNaN(progress) ? 0 : Math.clamp(progress, 0, 1);
        int w = (int) Math.round(width * ratio), h = (int) Math.round(height * ratio);
        return switch (direction) {
            case RIGHT -> new Rect(0, 0, w, height);
            case LEFT -> new Rect(width - w, 0, w, height);
            case UP -> new Rect(0, height - h, width, h);
            case DOWN -> new Rect(0, 0, width, h);
        };
    }

    @Override
    public List<Layer> layers(Rect bounds, double ticks, double progress) {
        float angle = !rotate
            ? 0
            : switch (direction) {
                case RIGHT -> 0;
                case LEFT -> (float) Math.PI;
                case UP -> (float) -Math.PI / 2;
                case DOWN -> (float) Math.PI / 2;
            };
        boolean vertical = rotate
            && (direction == ProgressDirection.UP || direction == ProgressDirection.DOWN);
        int w = vertical ? bounds.height() : bounds.width(),
            h = vertical ? bounds.width() : bounds.height();
        return List.of(
            new Layer(
                background,
                backgroundTint,
                new Rect(0, 0, bounds.width(), bounds.height()),
                angle,
                w,
                h),
            new Layer(sprite(), tint(), fill(bounds.width(), bounds.height(), progress), angle, w,
                h));
    }

    @Override
    public String toString() {
        return super.toString()
            + "["
            + background
            + ","
            + backgroundTint
            + ","
            + direction
            + ","
            + rotate
            + "]";
    }

    public Set<ValueType<?>> valueKinds(WidgetTypes widgets) {
        return NUMBERS;
    }

    public void validate() {
        super.validate();
        ValueContract.require(this, "a <progress-sprite>", true);
    }
}
