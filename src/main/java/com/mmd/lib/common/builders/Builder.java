package com.mmd.lib.common.builders;

import net.minecraft.resources.ResourceLocation;

public abstract class Builder<T, X> {
    public abstract T build(); // actually complete the creation process
    public abstract X setName(final ResourceLocation name);
    public abstract X setName(final String name);
}
