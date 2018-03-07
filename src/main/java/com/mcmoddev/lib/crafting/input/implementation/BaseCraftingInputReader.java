package com.mcmoddev.lib.crafting.input.implementation;

import com.mcmoddev.lib.crafting.base.implementation.BaseCraftingObject;
import com.mcmoddev.lib.crafting.input.ICraftingInputReader;

public abstract class BaseCraftingInputReader extends BaseCraftingObject implements ICraftingInputReader {
    protected BaseCraftingInputReader(String key) {
        super(key);
    }
}
