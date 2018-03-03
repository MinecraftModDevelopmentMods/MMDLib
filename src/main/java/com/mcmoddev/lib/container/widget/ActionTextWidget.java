package com.mcmoddev.lib.container.widget;

import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * An action widget implementation that handles text messages.
 */
@SuppressWarnings({"unused", "UnusedReturnValue", "WeakerAccess"})
public class ActionTextWidget extends ActionWidget {
    /**
     * Key used in nbt tag compound to reference the text to be set in this widget.
     */
    public final static String NBT_TEXT_KEY = "_text";

    private String text;

    /**
     * Initializes a new instance of ActionTextWidget.
     * @param key The key that uniquely identified this widget.
     * @param initialText The initial text value of this widget.
     */
    public ActionTextWidget(final String key, final String initialText) {
        super(key, true);
        this.text = initialText;
    }

    /**
     * Gets the text currently stored in this widget.
     * @return The text currently stored in this widget.
     */
    public String getText() {
        return this.text;
    }

    /**
     * Sets the text currently stored in this widget.
     * @param text The text to be stored in this widget.
     * @implNote This method should only be called on server side.
     */
    public void setText(@Nullable final String text) {
        if (((text == null) && (this.text == null)) || ((this.text != null) && this.text.equals(text))) {
            return; // same text, ignore it
        }

        this.text = text;
        this.setDirty();

        // TODO: maybe handle this method being called on client side.
    }

    /**
     * Sets a client side consumer of this widget.
     * @param consumer The consumer to be invoked when this action is triggered.
     * @return A reference back to this widget.
     */
    public ActionTextWidget setClientSideTextConsumer(final Consumer<String> consumer) {
        super.setClientSideConsumer(nbt -> {
            consumer.accept(this.getTextFromNBT(nbt));
        });
        return this;
    }

    /**
     * Sets a server side consumer of this widget.
     * @param consumer The consumer to be invoked when this action is triggered.
     * @return A reference back to this widget.
     */
    public ActionTextWidget setServerSideTextConsumer(final Consumer<String> consumer) {
        super.setServerSideConsumer(nbt -> {
            consumer.accept(this.getTextFromNBT(nbt));
        });
        return this;
    }

    @Nullable
    @Override
    public NBTTagCompound getUpdateCompound() {
        final NBTTagCompound nbt = new NBTTagCompound();
        if (this.text != null) {
            nbt.setString(NBT_TEXT_KEY, this.text);
        }
        return nbt;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleMessageFromServer(final NBTTagCompound tag) {
        // client side, don't care about dirty flag
        this.text = this.getTextFromNBT(tag);

        super.handleMessageFromServer(tag);
    }

    @Override
    public void handleMessageFromClient(final NBTTagCompound tag) {
        // server side, we do care about dirty flag
        this.setText(this.getTextFromNBT(tag));

        super.handleMessageFromClient(tag);
    }

    @Nullable
    private String getTextFromNBT(final NBTTagCompound tag) {
        return (tag.hasKey(NBT_TEXT_KEY, Constants.NBT.TAG_STRING))
            ? tag.getString(NBT_TEXT_KEY)
            : null;
    }

    /**
     * Gets an nbt tag compound that can be used as a client to server call to trigger an action text widget's consumers.
     * @param newText New text to be set on action text widget.
     * @return The nbt tag compound that can be used as a client to server call to trigger an action text widget's consumers.
     */
    @SideOnly(Side.CLIENT)
    public static NBTTagCompound getActionNBT(@Nullable final String newText) {
        final NBTTagCompound data = new NBTTagCompound();
        if (newText != null) {
            data.setString(NBT_TEXT_KEY, newText);
        }
        return data;
    }
}
