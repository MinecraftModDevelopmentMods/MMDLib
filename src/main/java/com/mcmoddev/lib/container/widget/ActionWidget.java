package com.mcmoddev.lib.container.widget;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.network.NBTBasedPlayerMessage;
import com.mcmoddev.lib.util.NBTUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SuppressWarnings("WeakerAccess")
public class ActionWidget extends BaseContextualWidget {
    private static final String ACTION_CALL_TAG_NAME = "_invokeAction";

    private BiConsumer<GuiContext, NBTTagCompound> clientConsumer = null;
    private BiConsumer<GuiContext, NBTTagCompound> serverConsumer = null;

    public ActionWidget(String key) {
        this(key, false);
    }

    protected ActionWidget(String key, boolean canBeDirty) {
        super(key, canBeDirty);
    }

    public ActionWidget setClientSideConsumer(Consumer<NBTTagCompound> consumer) {
        this.clientConsumer = (context, nbt) -> consumer.accept(nbt);
        return this;
    }

    public ActionWidget setClientSideConsumer(BiConsumer<GuiContext, NBTTagCompound> consumer) {
        this.clientConsumer = consumer;
        return this;
    }

    public ActionWidget setClientSideConsumer(Runnable consumer) {
        return this.setClientSideConsumer(nbt -> consumer.run());
    }

    public ActionWidget setServerSideConsumer(BiConsumer<GuiContext, NBTTagCompound> consumer) {
        this.serverConsumer = consumer;
        return this;
    }

    public ActionWidget setServerSideConsumer(Consumer<NBTTagCompound> consumer) {
        this.serverConsumer = (context, nbt) -> consumer.accept(nbt);
        return this;
    }

    public ActionWidget setServerSideConsumer(Runnable consumer) {
        return this.setServerSideConsumer(nbt -> consumer.run());
    }

    @Override
    public void handleMessageFromClient(NBTTagCompound tag) {
        if ((this.serverConsumer != null) && tag.hasKey(ACTION_CALL_TAG_NAME, Constants.NBT.TAG_COMPOUND)) {
            this.serverConsumer.accept(this.getContext(), tag.getCompoundTag(ACTION_CALL_TAG_NAME));
        }
    }

    @SideOnly(Side.CLIENT)
    public void actionPerformed() {
        this.actionPerformed(new NBTTagCompound());
    }

    @SideOnly(Side.CLIENT)
    public void actionPerformed(NBTTagCompound data) {
        if (this.clientConsumer != null) {
            this.clientConsumer.accept(this.getContext(), data);
        }

        if (this.serverConsumer != null) {
            // send package to server for further processing
            NBTTagCompound nbt = NBTUtils.wrapCompound(data, this.getKey(), ACTION_CALL_TAG_NAME);
            NBTBasedPlayerMessage.sendToServer(Minecraft.getMinecraft().player, nbt);
        }
    }
}
