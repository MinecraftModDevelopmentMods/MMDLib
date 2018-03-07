package com.mcmoddev.lib.util;

import javax.annotation.Nullable;

public final class StringUtils {
    private StringUtils() { }

    public static String insert(@Nullable String original, int index, String toInsert) {
        if ((original == null) || original.isEmpty()) {
            return toInsert;
        }

        if (index >= original.length()) {
            return original + toInsert;
        }
        if (index <= 0) {
            return toInsert + original;
        }

        String start = original.substring(0, index);
        String end = original.substring(index);
        return start + toInsert + end;
    }

    public static String remove(@Nullable String original, int from, int length) {
        if ((original == null) || (original.length() == 0)) {
            return "";
        }

        if (from <= 0) {
            length += from;
            if (length > 0) {
                if (length < original.length()) {
                    return original.substring(length);
                } else
                    return "";
            }
            return original;
        }

        if (from >= original.length()) {
            return original;
        }

        String start = original.substring(0, from);
        int endStart = from + length;
        if (endStart >= original.length()) {
            return start;
        }

        String end = original.substring(endStart);
        return start + end;
    }
}
