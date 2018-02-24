package com.mcmoddev.lib.feature;

import java.util.Arrays;
import java.util.List;
import net.minecraft.util.EnumFacing;

public interface ISidedFeature extends IFeature {
    EnumFacing[] getFacings();
    void setFacings(EnumFacing[] facings);

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

    default void disableFacing(final EnumFacing facing) {
        if (this.isFacingEnabled(facing)) {
            this.setFacings(Arrays
                .stream(this.getFacings())
                .filter(f -> (f != facing))
                .toArray(EnumFacing[]::new)
            );
        }
    }

    default boolean isFacingEnabled(final EnumFacing facing) {
        final EnumFacing[] facings = this.getFacings();
        return Arrays.stream(facings).anyMatch(f -> (f == facing));
    }
}
