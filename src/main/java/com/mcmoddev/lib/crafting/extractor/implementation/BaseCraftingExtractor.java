package com.mcmoddev.lib.crafting.extractor.implementation;

import com.mcmoddev.lib.crafting.base.implementation.BaseCraftingObject;
import com.mcmoddev.lib.crafting.extractor.ICraftingInputExtractor;

public abstract class BaseCraftingExtractor extends BaseCraftingObject implements ICraftingInputExtractor {
    protected BaseCraftingExtractor(String key) {
        super(key);
    }
}
