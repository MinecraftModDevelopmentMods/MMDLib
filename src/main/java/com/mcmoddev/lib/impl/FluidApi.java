package com.mcmoddev.lib.impl;

import com.mcmoddev.lib.API.IFluidApi;
import com.mcmoddev.lib.material.MMDMaterial;

import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

public class FluidApi implements IFluidApi {

	public static final IFluidApi instance = new FluidApi();

	@Override
	public String getApiName() {
		return "MMDLib::FluidApi";
	}

	@Override
	public Fluid createFluid(String typeName, MMDMaterial material) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public FluidStack getFluid(String typeName, MMDMaterial material, int amount) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IFluidApi addFluid(String typeName, MMDMaterial material) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IFluidApi addFluid(String typeName, Fluid fluid, MMDMaterial material) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IFluidApi addFluid(String typeName, Fluid fluid, BlockFluidBase fluidBlock, MMDMaterial material) {
		// TODO Auto-generated method stub
		return null;
	}

}
