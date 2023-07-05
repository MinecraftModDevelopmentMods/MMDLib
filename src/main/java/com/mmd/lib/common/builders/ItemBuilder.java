package com.mmd.lib.common.builders;

import com.mmd.lib.common.types.MMDItem;
import net.minecraft.resources.ResourceLocation;

public class ItemBuilder extends Builder<MMDItem, ItemBuilder>
{
    /**
     *  We have a <em>LOT</em> of options for this thing
     */
    private ResourceLocation itemName;

    public ItemBuilder() {
        super();
    }

    @Override
    public ItemBuilder setName(final ResourceLocation name) {
        itemName = name;
        return this;
    }

    @Override
    public ItemBuilder setName(final String name) {
        itemName = new ResourceLocation(name);
        return this;
    }

    @Override
    public MMDItem build() {
        // TODO: stub!
        return null;
    }
}


