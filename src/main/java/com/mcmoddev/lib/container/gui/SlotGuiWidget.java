package com.mcmoddev.lib.container.gui;

import net.minecraft.inventory.Slot;

public class SlotGuiWidget extends SpriteBackgroundGui {
    private final Slot slot;

    public SlotGuiWidget(Slot slot) {
        super(GuiSprites.MC_SLOT_BACKGROUND, 18, 18);

        this.slot = slot;
    }


}
