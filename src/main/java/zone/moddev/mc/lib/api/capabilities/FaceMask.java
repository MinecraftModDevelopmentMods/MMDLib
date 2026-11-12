package zone.moddev.mc.lib.api.capabilities;

import java.util.Set;

/** Bounded face-policy encoding shared by runtime configuration and UI snapshots. */
public final class FaceMask {
    public static final int ALL = 63;
    private FaceMask() {
    }
    public static int of(Set<RelativeFace> faces) {
        int mask = 0;
        for (var face : Set.copyOf(faces))
            mask |= 1 << face.ordinal();
        return mask;
    }
    public static int checked(int mask) {
        if (mask < 0 || mask > ALL)
            throw new IllegalArgumentException("Invalid face mask");
        return mask;
    }
    public static boolean available(int disabled, int face) {
        return face >= 0 && face < 6 && (disabled & (1 << face)) == 0;
    }
}
