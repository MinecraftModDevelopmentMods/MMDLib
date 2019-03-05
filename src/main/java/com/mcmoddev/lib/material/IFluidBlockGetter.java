package com.mcmoddev.lib.material;

import java.util.function.Function;

import net.minecraft.block.material.Material;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.FluidRegistry;

public interface IFluidBlockGetter extends Function<String, BlockFluidClassic> {
	BlockFluidClassic apply(String fluidName);
}
