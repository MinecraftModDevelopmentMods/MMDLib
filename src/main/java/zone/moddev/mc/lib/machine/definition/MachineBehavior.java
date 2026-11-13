package zone.moddev.mc.lib.machine.definition;

import java.util.List;

/** Optional physical/item policies and a mod-owned processor. */
public record MachineBehavior(ProcessorDeclaration processor, int stackSize,
    boolean noOcclusion, boolean immovable, boolean portable, boolean drawers,
    List<Shape> shapes,
    net.minecraft.resources.ResourceLocation secondaryAction,
    EntityDisplay entityDisplay) {
    public MachineBehavior(ProcessorDeclaration processor, int stackSize,
        boolean noOcclusion, boolean immovable, boolean portable,
        boolean drawers, List<Shape> shapes,
        net.minecraft.resources.ResourceLocation secondaryAction) {
        this(processor, stackSize, noOcclusion, immovable, portable, drawers,
            shapes, secondaryAction, null);
    }
    public static final MachineBehavior DEFAULT = new MachineBehavior(null, 64,
        false, false, false, true, List.of(), null);
    public MachineBehavior {
        if (stackSize < 1 || stackSize > 64)
            throw new IllegalArgumentException("Stack size must be 1..64");
        if (portable && (processor == null
            || !zone.moddev.mc.lib.machine.feature.PortableProcessor.class
                .isAssignableFrom(processor.type())))
            throw new IllegalArgumentException(
                "Portable machines require a PortableProcessor");
        shapes = List.copyOf(shapes);
    }

    /**
     * Axis-aligned, block-local bounds, using the same 0..16 coordinates as
     * model boxes.
     */
    public record Shape(String id, String when, double x1, double y1, double z1,
        double x2, double y2, double z2) {
        public Shape {
            if (!(x1 >= 0 && y1 >= 0 && z1 >= 0 && x2 <= 16 && y2 <= 16
                && z2 <= 16 && x1 < x2 && y1 < y2 && z1 < z2))
                throw new IllegalArgumentException(
                    "Invalid machine shape: " + id);
        }
    }
}
