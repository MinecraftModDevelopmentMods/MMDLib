package com.mcmoddev.lib.gui;

import net.minecraft.util.ResourceLocation;

public enum GuiTextures implements IGuiTexture {
    MC_DEMO_BACKGROUND(new ResourceLocation("minecraft", "textures/gui/demo_background.png")),
    MC_GENERIC_54(new ResourceLocation("minecraft", "textures/gui/container/generic_54.png"))
    ;

    private final ResourceLocation resource;

    GuiTextures(ResourceLocation resource) {
        this.resource = resource;
    }

    @Override
    public ResourceLocation getResource() {
        return this.resource;
    }
}
