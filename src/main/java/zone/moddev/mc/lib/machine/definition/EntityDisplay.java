package zone.moddev.mc.lib.machine.definition;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.api.entity.EntitySnapshot;
import zone.moddev.mc.lib.gui.model.ProcessorVariable;
import zone.moddev.mc.lib.machine.feature.Processor;

/**
 * Declarative detached-entity overlay; coordinates and fit bounds use block
 * units.
 */
public record EntityDisplay(ProcessorVariable variable, double x, double y,
    double z, float width, float height, float yaw, float itemYaw,
    boolean block, boolean item, ResourceLocation itemProperty) {
    public EntityDisplay {
        if (variable == null || !Double.isFinite(x) || !Double.isFinite(y)
            || !Double.isFinite(z) || Math.abs(x) > 16 || Math.abs(y) > 16
            || Math.abs(z) > 16 || !Float.isFinite(width)
            || !Float.isFinite(height) || width <= 0 || height <= 0
            || width > 16 || height > 16 || !Float.isFinite(yaw)
            || !Float.isFinite(itemYaw) || Math.abs(yaw) > 360
            || Math.abs(itemYaw) > 360 || (!block && !item)
            || (!item && itemProperty != null))
            throw new IllegalArgumentException(
                "Invalid entity display bounds or targets");
    }

    public void validate(Map<String, Processor<?>> processors) {
        if (!(variable.defaultValue(processors) instanceof EntitySnapshot))
            throw new IllegalArgumentException(
                "Entity display requires a synchronized EntitySnapshot variable");
        if (item) {
            var reference = processors.get(variable.processor())
                .portableVariable(variable.name())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Item entity display requires a PortableVariable declaration"));
            if (!reference.processor().equals(variable.processor()))
                throw new IllegalArgumentException(
                    "Portable variable processor does not match entity display");
        }
    }

    public net.minecraft.world.phys.Vec3 offset(
        net.minecraft.core.Direction facing) {
        return switch (facing) {
            case NORTH -> new net.minecraft.world.phys.Vec3(x, y, z);
            case EAST -> new net.minecraft.world.phys.Vec3(1 - z, y, x);
            case SOUTH -> new net.minecraft.world.phys.Vec3(1 - x, y, 1 - z);
            case WEST -> new net.minecraft.world.phys.Vec3(z, y, 1 - x);
            default -> throw new IllegalArgumentException(
                "Entity display requires horizontal facing");
        };
    }
}
