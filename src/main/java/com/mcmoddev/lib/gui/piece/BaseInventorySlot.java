package com.mcmoddev.lib.gui.piece;

import javax.annotation.Nullable;
import com.mcmoddev.lib.gui.GuiSprites;
import com.mcmoddev.lib.gui.IContainerSlot;
import com.mcmoddev.lib.gui.IGuiSprite;
import com.mcmoddev.lib.gui.MMDGuiContainer;
import com.mcmoddev.lib.gui.util.Size2D;
import net.minecraft.inventory.Slot;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public abstract class BaseInventorySlot extends BaseGuiPiece implements IContainerSlot {
    public static final int WIDTH = 18;
    public static final int HEIGHT = 18;
    public static final IGuiSprite DEFAULT_BG_SPRITE = GuiSprites.MC_SLOT_BACKGROUND;

    private IGuiSprite bgSprite;
    private final String groupId;
    private final String subGroupId;
    private final int index;

    protected BaseInventorySlot(String groupId, String subGroupId, int index, @Nullable IGuiSprite bgSprite) {
        super(WIDTH, HEIGHT);
        this.bgSprite = bgSprite;

        this.groupId = groupId;
        this.subGroupId = subGroupId;
        this.index = index;
    }

    protected BaseInventorySlot(String groupId, String subGroupId, int index) {
        this(groupId, subGroupId, index, DEFAULT_BG_SPRITE);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawBackgroundLayer(MMDGuiContainer container, float partialTicks, int mouseX, int mouseY) {
        if (this.bgSprite != null) {
            this.bgSprite.draw(container, 0, 0);
        }
    }

    @Override
    public Slot getSlot() {
        Slot slot = this.getInternalSlot();

        Size2D offset = this.getRenderOffset();
        slot.xPos = offset.width;
        slot.yPos = offset.height;

        return slot;
    }

    protected abstract Slot getInternalSlot();

    @Override
    public String getGroupId() {
        return this.groupId;
    }

    @Override
    public String getSubGroupId() {
        return this.subGroupId;
    }

    @Override
    public int getIndex() {
        return this.index;
    }
}
