package com.mcmoddev.lib.crafting.input.implementation;

import net.minecraft.item.Item;

// needed by Face of Cat for Forestry integration of his Powered Thingies' Tree Farm.
// in one was wondering
public class ClassNameFilterInput extends BaseItemRegistryFilterInput {
    private final String classNamePattern;

    public ClassNameFilterInput(String key, String classNamePattern, int stackCount, int stackMeta) {
        super(key, stackCount, stackMeta);
        this.classNamePattern = classNamePattern;
    }

    @Override
    boolean isMatch(Item item) {
        return BaseItemRegistryFilterInput.isNameMatch(this.classNamePattern, item.getClass().getSimpleName());
    }
}
