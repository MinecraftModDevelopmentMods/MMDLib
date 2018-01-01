package com.mcmoddev.lib.gui.util;

public class Size2D {
    public final int width;
    public final int height;

    public static final Size2D ZERO = new Size2D(0);

    public Size2D(int uniform) {
        this(uniform, uniform);
    }

    public Size2D(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public Size2D add(int horizontal, int vertical) {
        return new Size2D(this.width + horizontal, this.height + vertical);
    }
}
