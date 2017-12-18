package com.mcmoddev.lib.API;

import com.mcmoddev.lib.material.MMDMaterial;

import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

public interface IFluidApi extends IMMDApi {

	default String getApiName() {
		return "MMDLib Fluid->Material Registration API";
	}

	Fluid createFluid( String typeName, MMDMaterial material );
	FluidStack getFluid( String typeName, MMDMaterial material, int amount );
	
	IFluidApi addFluid( String typeName, MMDMaterial material );
	IFluidApi addFluid( String typeName, Fluid fluid, MMDMaterial material );
	IFluidApi addFluid( String typeName, Fluid fluid, BlockFluidBase fluidBlock, MMDMaterial material );
}
