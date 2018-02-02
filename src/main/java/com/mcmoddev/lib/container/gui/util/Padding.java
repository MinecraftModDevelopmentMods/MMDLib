package com.mcmoddev.lib.container.gui.util;

public class Padding {
    public final int left;
    public final int top;
    public final int right;
    public final int bottom;

    public static final Padding EMPTY = new Padding(0);

    public Padding(int uniform) {
        this(uniform, uniform, uniform, uniform);
    }

    public Padding(int leftRight, int topBottom) {
        this(leftRight, topBottom, leftRight, topBottom);
    }

    public Padding(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public boolean isEmpty() {
        return (this.getHorizontal() == 0) && (this.getVertical() == 0);
    }

    public int getHorizontal() {
        return this.left + this.right;
    }

    public int getVertical() {
        return this.top + this.bottom;
    }

    public static Padding left(int left) {
        return new Padding(left, 0, 0, 0);
    }

    public static Padding top(int top) {
        return new Padding(0, top, 0, 0);
    }

    public static Padding right(int right) {
        return new Padding(0, 0, right, 0);
    }

    public static Padding bottom(int bottom) {
        return new Padding(0, 0, 0, bottom);
    }
}
