package com.mcmoddev.lib.crafting.input.implementation;

import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.oredict.OreDictionary;

public class OreDictionaryItemInput extends BaseItemInput {
    private final String oreDictKey;

    public OreDictionaryItemInput(String key, String oreDictKey, int amount) {
        super(key, amount);
        this.oreDictKey = oreDictKey;
    }

    @Override
    void populatePossibleInputs(NonNullList<ItemStack> stacks) {
        stacks.addAll(OreDictionary.getOres(this.oreDictKey));
    }
}
