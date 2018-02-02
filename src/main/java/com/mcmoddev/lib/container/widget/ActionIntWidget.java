package com.mcmoddev.lib.container.widget;

import java.util.function.IntConsumer;
import javax.annotation.Nullable;
import com.mcmoddev.lib.util.NBTUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ActionIntWidget extends ActionWidget {
    public final static String NBT_VALUE_KEY = "_value";

    private int value;

    public ActionIntWidget(String key, int initialValue) {
        super(key, true);
        this.value = initialValue;
    }

    public int getValue() {
        return this.value;
    }

    public void setValue(int value) {
        if (value == this.value) {
            return; // same value, ignore it
        }

        this.value = value;
        this.setDirty();
    }

    public ActionIntWidget setClientSideConsumer(IntConsumer consumer) {
        super.setClientSideConsumer(nbt -> {
            consumer.accept(this.getValueFromNBT(nbt));
        });
        return this;
    }

    public ActionIntWidget setServerSideConsumer(IntConsumer consumer) {
        super.setServerSideConsumer(nbt -> {
            consumer.accept(this.getValueFromNBT(nbt));
        });
        return this;
    }

    @Nullable
    @Override
    public NBTTagCompound getUpdateCompound() {
        NBTTagCompound nbt = new NBTTagCompound();
        if (this.value != 0) {
            nbt.setInteger(NBT_VALUE_KEY, this.value);
        }
        return nbt;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleMessageFromServer(NBTTagCompound tag) {
        // client side, don't care about dirty flag
        this.value = this.getValueFromNBT(tag);
    }

    @Override
    public void handleMessageFromClient(NBTTagCompound tag) {
        // server side, we do care about dirty flag
        this.setValue(this.getValueFromNBT(tag));
    }

    @Nullable
    private int getValueFromNBT(NBTTagCompound tag) {
        return (tag.hasKey(NBT_VALUE_KEY, Constants.NBT.TAG_INT))
            ? tag.getInteger(NBT_VALUE_KEY)
            : 0;
    }

    @SideOnly(Side.CLIENT)
    public static NBTTagCompound getActionNBT(String widgetKey, int newValue) {
        NBTTagCompound data = new NBTTagCompound();
        if (newValue != 0) {
            data.setInteger(NBT_VALUE_KEY, newValue);
        }
        return NBTUtils.wrapCompound(data, widgetKey);
    }
}
