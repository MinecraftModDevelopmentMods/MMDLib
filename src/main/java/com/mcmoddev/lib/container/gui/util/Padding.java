package com.mcmoddev.lib.container.gui.util;

import com.mcmoddev.lib.container.gui.IWidgetGui;

/**
 * Represents the padding around a {@link IWidgetGui}.
 */
public class Padding {
    /**
     * Left padding.
     */
    public final int left;
    /**
     * Top padding.
     */
    public final int top;
    /**
     * Right padding.
     */
    public final int right;
    /**
     * Bottom padding.
     */
    public final int bottom;

    /**
     * Value indicating a padding of 0 on all sides.
     */
    public static final Padding EMPTY = new Padding(0);

    /**
     * Initializes a new instance of Padding.
     * @param uniform Value to be used for all sides.
     */
    public Padding(final int uniform) {
        this(uniform, uniform, uniform, uniform);
    }

    /**
     * Initializes a new instance of Padding.
     * @param leftRight Value to be used for left and right sides.
     * @param topBottom Value to be used for top and bottom sides.
     */
    public Padding(final int leftRight, final int topBottom) {
        this(leftRight, topBottom, leftRight, topBottom);
    }

    /**
     * Initializes a new instance of Padding.
     * @param left Left padding.
     * @param top Top padding.
     * @param right Right padding.
     * @param bottom Bottom padding.
     */
    public Padding(final int left, final int top, final int right, final int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    /**
     * Tests if all sides have a padding of 0.
     * @return True if padding is 0 for all sides. False otherwise.
     */
    public boolean isEmpty() {
        return (this.getHorizontal() == 0) && (this.getVertical() == 0);
    }

    /**
     * Gets combined left and right paddings.
     * @return The combined value of left and right paddings.
     */
    public int getHorizontal() {
        return this.left + this.right;
    }

    /**
     * Gets combined top and bottom paddings.
     * @return The combined value of top and bottom paddings.
     */
    public int getVertical() {
        return this.top + this.bottom;
    }

    /**
     * Creates a new padding with only left value set.
     * @param left The left padding.
     * @return A new padding with only left value set.
     */
    public static Padding left(final int left) {
        return new Padding(left, 0, 0, 0);
    }

    /**
     * Creates a new padding with only top value set.
     * @param top The top padding.
     * @return A new padding with only top value set.
     */
    public static Padding top(final int top) {
        return new Padding(0, top, 0, 0);
    }

    /**
     * Creates a new padding with only right value set.
     * @param right The right padding.
     * @return A new padding with only right value set.
     */
    public static Padding right(final int right) {
        return new Padding(0, 0, right, 0);
    }

    /**
     * Creates a new padding with only bottom value set.
     * @param bottom The bottom padding.
     * @return A new padding with only bottom value set.
     */
    public static Padding bottom(final int bottom) {
        return new Padding(0, 0, 0, bottom);
    }

    /**
     * Creates a new padding with all sides set to same value.
     * @param size The value to be used for all sides.
     * @return A new padding with all sides set to same value.
     */
    public static Padding uniform(final int size) { return new Padding(size); }
}
