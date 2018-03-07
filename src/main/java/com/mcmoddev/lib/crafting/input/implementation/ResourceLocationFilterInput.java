package com.mcmoddev.lib.crafting.input.implementation;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

public class ResourceLocationFilterInput extends BaseItemRegistryFilterInput {
    private final String resourcePattern;

    public ResourceLocationFilterInput(String key, String resourcePattern, int stackCount, int stackMeta) {
        super(key, stackCount, stackMeta);
        this.resourcePattern = resourcePattern;
    }

    @Override
    boolean isMatch(Item item) {
        ResourceLocation name = item.getRegistryName();
        if (name != null) {
            String[] parts = this.resourcePattern.split(":", 2);
            if (parts.length == 1) {
                return BaseItemRegistryFilterInput.isNameMatch(parts[0], name.getResourcePath());
            }
            else {
                return BaseItemRegistryFilterInput.isNameMatch(parts[0], name.getResourceDomain())
                    && BaseItemRegistryFilterInput.isNameMatch(parts[1], name.getResourcePath());
            }
        }
        return false;
    }
}
