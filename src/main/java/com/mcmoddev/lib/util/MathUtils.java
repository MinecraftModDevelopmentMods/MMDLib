package com.mcmoddev.lib.util;

public final class MathUtils {
    private MathUtils() {}

    public static int clampi(int value, int min, int max) {
        if (max < min) {
            int temp = max;
            max = min;
            min = temp;
        }

        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    public static float clampf(float value, float min, float max) {
        if (max < min) {
            float temp = max;
            max = min;
            min = temp;
        }

        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
