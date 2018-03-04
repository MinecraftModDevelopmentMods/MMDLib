package com.mcmoddev.lib.container.gui.util;

/**
 * Stores a 2D size.
 */
public class Size2D {
    /**
     * Width.
     */
    public final int width;
    /**
     * Height.
     */
    public final int height;

    /**
     * Represents a size with 0 width and 0 height.
     */
    public static final Size2D ZERO = new Size2D(0);

    /**
     * Initializes a new instance of Size2D.
     * @param uniform Value to be used for both width and height.
     */
    public Size2D(final int uniform) {
        this(uniform, uniform);
    }

    /**
     * Initializes a new instance of Size2D.
     * @param width Width.
     * @param height Height.
     */
    public Size2D(final int width, final int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Adds some values to the current size creating a new size object.
     * @param horizontal Value to add to width.
     * @param vertical Value to add to height.
     * @return A new instance of Size2D representing the summed value of width and height.
     */
    public Size2D add(final int horizontal, final int vertical) {
        return new Size2D(this.width + horizontal, this.height + vertical);
    }
}
