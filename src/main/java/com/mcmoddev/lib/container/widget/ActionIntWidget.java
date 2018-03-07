package com.mcmoddev.lib.container.widget;

import java.util.function.IntConsumer;
import javax.annotation.Nullable;
import com.mcmoddev.lib.util.NBTUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * An action widget implementation that handles a single int data.
 */
@SuppressWarnings({"unused", "UnusedReturnValue", "WeakerAccess"})
public class ActionIntWidget extends ActionWidget {
    /**
     * Key used in nbt tag compound to reference the value to be set in this widget.
     */
    public final static String NBT_VALUE_KEY = "_value";

    private int value;

    /**
     * Initializes a new instance of ActionIntWidget.
     * @param key The key that uniquely identified this widget.
     * @param initialValue The initial value of this widget.
     */
    public ActionIntWidget(final String key, final int initialValue) {
        super(key, true);
        this.value = initialValue;
    }

    /**
     * Gets the value currently stored in this widget.
     * @return The value currently stored in this widget.
     */
    public int getValue() {
        return this.value;
    }

    /**
     * Sets the value currently stored in this widget.
     * @param value The value to be stored in this widget.
     * @implNote This method should only be called on server side.
     */
    public void setValue(final int value) {
        if (value == this.value) {
            return; // same value, ignore it
        }

        this.value = value;
        this.setDirty();
    }

    /**
     * Sets a client side consumer of this widget.
     * @param consumer The consumer to be invoked when this action is triggered.
     * @return A reference back to this widget.
     */
    public ActionIntWidget setClientSideConsumer(final IntConsumer consumer) {
        super.setClientSideConsumer(nbt -> {
            consumer.accept(this.getValueFromNBT(nbt));
        });
        return this;
    }

    /**
     * Sets a server side consumer of this widget.
     * @param consumer The consumer to be invoked when this action is triggered.
     * @return A reference back to this widget.
     */
    public ActionIntWidget setServerSideConsumer(final IntConsumer consumer) {
        super.setServerSideConsumer(nbt -> {
            consumer.accept(this.getValueFromNBT(nbt));
        });
        return this;
    }

    @Nullable
    @Override
    public NBTTagCompound getUpdateCompound() {
        final NBTTagCompound nbt = new NBTTagCompound();
        if (this.value != 0) {
            nbt.setInteger(NBT_VALUE_KEY, this.value);
        }
        return nbt;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleMessageFromServer(final NBTTagCompound tag) {
        // client side, don't care about dirty flag
        this.value = this.getValueFromNBT(tag);
    }

    @Override
    public void handleMessageFromClient(final NBTTagCompound tag) {
        // server side, we do care about dirty flag
        this.setValue(this.getValueFromNBT(tag));
    }

    private int getValueFromNBT(final NBTTagCompound tag) {
        return (tag.hasKey(NBT_VALUE_KEY, Constants.NBT.TAG_INT))
            ? tag.getInteger(NBT_VALUE_KEY)
            : 0;
    }

    /**
     * Gets an nbt tag compound that can be used as a client to server call to trigger an action int widget's consumers.
     * @param newValue New value to be set on the action int widget.
     * @return The nbt tag compound that can be used as a client to server call to trigger an action int widget's consumers.
     */
    @SideOnly(Side.CLIENT)
    public static NBTTagCompound getActionNBT(final String widgetKey, final int newValue) {
        final NBTTagCompound data = new NBTTagCompound();
        if (newValue != 0) {
            data.setInteger(NBT_VALUE_KEY, newValue);
        }
        return NBTUtils.wrapCompound(data, widgetKey);
    }
}
