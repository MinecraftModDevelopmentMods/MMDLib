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

/**
 * Base implementation of a widget than can handle GUI interactions.
 * Can be used to react to interactions on both client and/or server side without having to deal with network packets.
 */
public class ActionWidget extends BaseContextualWidget {
    private static final String ACTION_CALL_TAG_NAME = "_invokeAction";

    private BiConsumer<GuiContext, NBTTagCompound> clientConsumer = null;
    private BiConsumer<GuiContext, NBTTagCompound> serverConsumer = null;

    /**
     * Initializes a new instance of ActionWidget.
     * @param key The key that uniquely identified this widget.
     */
    public ActionWidget(final String key) {
        this(key, false);
    }

    /**
     * Initializes a new instance of ActionWidget.
     * @param key The key that uniquely identified this widget.
     * @param canBeDirty Specified if this widgets can get dirty or it only contains static data.
     */
    protected ActionWidget(final String key, final boolean canBeDirty) {
        super(key, canBeDirty);
    }

    /**
     * Sets a client side consumer of this widget.
     * @param consumer The consumer to be invoked when this action is triggered.
     * @return A reference back to this widget.
     */
    public ActionWidget setClientSideConsumer(final Consumer<NBTTagCompound> consumer) {
        this.clientConsumer = (context, nbt) -> consumer.accept(nbt);
        return this;
    }

    /**
     * Sets a client side consumer of this widget.
     * @param consumer The consumer to be invoked when this action is triggered.
     * @return A reference back to this widget.
     */
    public ActionWidget setClientSideConsumer(final BiConsumer<GuiContext, NBTTagCompound> consumer) {
        this.clientConsumer = consumer;
        return this;
    }

    /**
     * Sets a client side consumer of this widget.
     * @param consumer The runnable to be invoked when this action is triggered.
     * @return A reference back to this widget.
     */
    public ActionWidget setClientSideConsumer(final Runnable consumer) {
        return this.setClientSideConsumer(nbt -> consumer.run());
    }

    /**
     * Sets a server side consumer of this widget.
     * @param consumer The consumer to be invoked when this action is triggered.
     * @return A reference back to this widget.
     */
    public ActionWidget setServerSideConsumer(final BiConsumer<GuiContext, NBTTagCompound> consumer) {
        this.serverConsumer = consumer;
        return this;
    }

    /**
     * Sets a server side consumer of this widget.
     * @param consumer The consumer to be invoked when this action is triggered.
     * @return A reference back to this widget.
     */
    public ActionWidget setServerSideConsumer(final Consumer<NBTTagCompound> consumer) {
        this.serverConsumer = (context, nbt) -> consumer.accept(nbt);
        return this;
    }

    /**
     * Sets a server side consumer of this widget.
     * @param consumer The consumer to be invoked when this action is triggered.
     * @return A reference back to this widget.
     */
    public ActionWidget setServerSideConsumer(final Runnable consumer) {
        return this.setServerSideConsumer(nbt -> consumer.run());
    }

    @Override
    public void handleMessageFromClient(final NBTTagCompound tag) {
        if ((this.serverConsumer != null) && tag.hasKey(ACTION_CALL_TAG_NAME, Constants.NBT.TAG_COMPOUND)) {
            this.serverConsumer.accept(this.getContext(), tag.getCompoundTag(ACTION_CALL_TAG_NAME));
        }
    }

    /**
     * Invokes all consumers of this action widget.
     */
    @SideOnly(Side.CLIENT)
    public void actionPerformed() {
        this.actionPerformed(new NBTTagCompound());
    }

    /**
     * Invokes all consumers of this action widget.
     * @param data The nbt tag compound to be sent to consumers.
     */
    @SideOnly(Side.CLIENT)
    public void actionPerformed(final NBTTagCompound data) {
        if (this.clientConsumer != null) {
            this.clientConsumer.accept(this.getContext(), data);
        }

        if (this.serverConsumer != null) {
            // send package to server for further processing
            final NBTTagCompound nbt = NBTUtils.wrapCompound(data, this.getKey(), ACTION_CALL_TAG_NAME);
            NBTBasedPlayerMessage.sendToServer(Minecraft.getMinecraft().player, nbt);
        }
    }
}
