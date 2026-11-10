package zone.moddev.mc.lib.gui.model.elements;

import java.util.List;
import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;

/** A GUI-atlas sprite. Subclasses select layers without depending on client rendering classes. */
public class SpriteElement implements Element {
    private final Common common;
    private final ResourceLocation sprite;
    private final int width, height, tint;

    public SpriteElement(Common common, ResourceLocation sprite, int width, int height) {
        this(common, sprite, width, height, 0xffffffff);
    }

    public SpriteElement(Common common, ResourceLocation sprite, int width, int height, int tint) {
        this.common = Objects.requireNonNull(common);
        this.sprite = Objects.requireNonNull(sprite);
        if (width <= 0 || height <= 0)
            throw new IllegalArgumentException("Sprite dimensions must be positive");
        this.width = width;
        this.height = height;
        this.tint = tint;
    }

    public Common common() {
        return common;
    }

    public ResourceLocation sprite() {
        return sprite;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int tint() {
        return tint;
    }

    public int[] measure(TextMeasure text) {
        return new int[]{width, height};
    }

    /** Clip coordinates are relative to the element; rotation is about its center, in radians. */
    public record Layer(
        ResourceLocation sprite, int tint, Rect clip, float rotation, int width, int height) {
    }

    public List<Layer> layers(Rect bounds, double ticks, double progress) {
        return List.of(
            new Layer(
                sprite,
                tint,
                new Rect(0, 0, bounds.width(), bounds.height()),
                0,
                bounds.width(),
                bounds.height()));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
            + "["
            + common
            + ","
            + sprite
            + ","
            + width
            + ","
            + height
            + ","
            + tint
            + "]";
    }
}
