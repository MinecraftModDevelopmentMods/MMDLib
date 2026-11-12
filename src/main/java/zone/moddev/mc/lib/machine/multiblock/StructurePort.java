package zone.moddev.mc.lib.machine.multiblock;

import java.util.Set;

import net.minecraft.core.Direction;

/** Port permissions narrow an already-filtered controller resource view. */
public record StructurePort(String member, String resource, Kind kind, Set<Direction> faces,
    boolean unsided, boolean input, boolean output) {
    public enum Kind {
        ITEMS, FLUIDS, ENERGY
    }
    public StructurePort {
        if (member.isBlank() || resource.isBlank() || member.length() > 128
            || resource.length() > 128)
            throw new IllegalArgumentException("Invalid port binding");
        java.util.Objects.requireNonNull(kind);
        faces = Set.copyOf(faces);
    }
    public boolean exposes(Direction local) {
        return local == null ? unsided : faces.contains(local);
    }
}
