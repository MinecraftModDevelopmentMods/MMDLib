package zone.moddev.mc.lib.machine.definition;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.gui.model.ProcessorVariable;
import zone.moddev.mc.lib.machine.feature.Processor;

/** A flat item preview on the front face, measured in block units. */
public record ItemDisplay(ProcessorVariable variable, double x, double y,
    float width, float height) {
    public ItemDisplay {
        if (variable == null || !Double.isFinite(x) || !Double.isFinite(y)
            || x < 0 || x > 1 || y < 0 || y > 1 || !Float.isFinite(width)
            || !Float.isFinite(height) || width <= 0 || width > 1 || height <= 0
            || height > 1)
            throw new IllegalArgumentException(
                "Item display requires a position in 0..1 and dimensions in (0, 1]");
    }

    public void validate(Map<String, Processor<?>> processors) {
        var value = variable.defaultValue(processors);
        if (!(value instanceof ItemStack) && !(value instanceof String))
            throw new IllegalArgumentException(
                "Item display requires a synchronized ItemStack or item-ID string variable");
        if (value instanceof String id && !id.isEmpty()
            && ResourceLocation.tryParse(id) == null)
            throw new IllegalArgumentException(
                "Invalid default item display ID: " + id);
    }

    /**
     * Position just outside the front face; x increases to the viewer's right.
     */
    public net.minecraft.world.phys.Vec3 offset(
        net.minecraft.core.Direction facing) {
        return switch (facing) {
            case SOUTH -> new net.minecraft.world.phys.Vec3(x, y, 1.002);
            case NORTH -> new net.minecraft.world.phys.Vec3(1 - x, y, -.002);
            case EAST -> new net.minecraft.world.phys.Vec3(1.002, y, 1 - x);
            case WEST -> new net.minecraft.world.phys.Vec3(-.002, y, x);
            default -> throw new IllegalArgumentException(
                "Item display requires horizontal facing");
        };
    }
}
