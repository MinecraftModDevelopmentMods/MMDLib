package com.mcmoddev.lib.crafting.base.implementation;

import com.mcmoddev.lib.crafting.base.ICraftingObject;

public abstract class BaseCraftingObject implements ICraftingObject {
    private String key;

    protected BaseCraftingObject(String key) {
        this.key = key;
    }

    @Override
    public String getKey() {
        return this.key;
    }
}
