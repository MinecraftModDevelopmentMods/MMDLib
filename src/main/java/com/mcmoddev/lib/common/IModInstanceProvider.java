package com.mcmoddev.lib.common;

import net.minecraft.world.World;

public interface IModInstanceProvider {
    Object getModInstance(World world);
}
