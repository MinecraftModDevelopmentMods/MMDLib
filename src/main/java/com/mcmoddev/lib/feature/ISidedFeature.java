package com.mcmoddev.lib.feature;

import java.util.Arrays;
import java.util.List;
import net.minecraft.util.EnumFacing;

/**
 * Interface implemented by features that have capabilities that are only accessible externally from certain faces.
 */
public interface ISidedFeature extends IFeature {
    /**
     * Gets the color of this sided feature. Used by the side configurator to visually identify this feature.
     * @return The color of this sided feature.
     */
    int getColor();

    /**
     * Gets the priority index of this feature. Used by the side configurator for sorting purposes.
     * @return The priority index of this feature.
     */
    int getPriorityIndex();

    /**
     * Gets the list of facings that are enabled.
     * @return The list of enabled facings.
     */
    EnumFacing[] getFacings();

    /**
     * Sets the list of facings that are enabled;
     * @param facings The list of enabled facings.
     */
    void setFacings(EnumFacing[] facings);

    /**
     * Makes sure the specified facing is enabled.
     * @param facing The facing to be enabled.
     */
    default void enableFacing(final EnumFacing facing) {
        if (!this.isFacingEnabled(facing)) {
            final EnumFacing[] facings = this.getFacings();
            if ((facings == null) || (facings.length == 0)) {
                this.setFacings(new EnumFacing[] { facing });
            }
            else {
                final List<EnumFacing> list = Arrays.asList(facings);
                list.add(facing);
                this.setFacings(list.toArray(new EnumFacing[facings.length + 1]));
            }
        }
    }

    /**
     * Makes sure the specified facing is disabled.
     * @param facing The facing to be disabled.
     */
    default void disableFacing(final EnumFacing facing) {
        if (this.isFacingEnabled(facing)) {
            this.setFacings(Arrays
                .stream(this.getFacings())
                .filter(f -> (f != facing))
                .toArray(EnumFacing[]::new)
            );
        }
    }

    /**
     * Tests if a specified facing is enabled.
     * @param facing The facing to be tested.
     * @return True if the specified facing is enabled. False otherwise.
     */
    default boolean isFacingEnabled(final EnumFacing facing) {
        final EnumFacing[] facings = this.getFacings();
        return Arrays.stream(facings).anyMatch(f -> (f == facing));
    }
}
