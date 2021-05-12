package com.mcmoddev.lib.material;

import java.util.function.Function;

import net.minecraftforge.fluids.BlockFluidClassic;

public interface IFluidBlockGetter extends Function<String, BlockFluidClassic> {
	BlockFluidClassic apply(String fluidName);
}
