package com.mcmoddev.lib.feature;

import java.util.Arrays;
import java.util.List;
import net.minecraft.util.EnumFacing;

public interface ISidedFeature extends IFeature {
    EnumFacing[] getFacings();
    void setFacings(EnumFacing[] facings);

    default void enableFacing(EnumFacing facing) {
        if (!this.isFacingEnabled(facing)) {
            EnumFacing[] facings = this.getFacings();
            if ((facings == null) || (facings.length == 0)) {
                this.setFacings(new EnumFacing[] { facing });
            }
            else {
                List<EnumFacing> list = Arrays.asList(facings);
                list.add(facing);
                this.setFacings(list.toArray(new EnumFacing[facings.length + 1]));
            }
        }
    }

    default void disableFacing(EnumFacing facing) {
        if (this.isFacingEnabled(facing)) {
            this.setFacings(Arrays
                .stream(this.getFacings())
                .filter(f -> (f != facing))
                .toArray(EnumFacing[]::new)
            );
        }
    }

    default boolean isFacingEnabled(EnumFacing facing) {
        EnumFacing[] facings = this.getFacings();
        return (facings != null) && Arrays.stream(facings).anyMatch(f -> (f == facing));
    }
}
