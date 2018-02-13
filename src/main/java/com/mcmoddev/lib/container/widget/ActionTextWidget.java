package com.mcmoddev.lib.container.widget;

import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ActionTextWidget extends ActionWidget {
    public final static String NBT_TEXT_KEY = "_text";

    private String text;

    public ActionTextWidget(String key, String initialText) {
        super(key, true);
        this.text = initialText;
    }

    public String getText() {
        return this.text;
    }

    public void setText(@Nullable String text) {
        if (((text == null) && (this.text == null)) || ((this.text != null) && this.text.equals(text))) {
            return; // same text, ignore it
        }

        this.text = text;
        this.setDirty();
    }

    public ActionTextWidget setClientSideTextConsumer(Consumer<String> consumer) {
        super.setClientSideConsumer(nbt -> {
            consumer.accept(this.getTextFromNBT(nbt));
        });
        return this;
    }

    public ActionTextWidget setServerSideTextConsumer(Consumer<String> consumer) {
        super.setServerSideConsumer(nbt -> {
            consumer.accept(this.getTextFromNBT(nbt));
        });
        return this;
    }

    @Nullable
    @Override
    public NBTTagCompound getUpdateCompound() {
        NBTTagCompound nbt = new NBTTagCompound();
        if (this.text != null) {
            nbt.setString(NBT_TEXT_KEY, this.text);
        }
        return nbt;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleMessageFromServer(NBTTagCompound tag) {
        // client side, don't care about dirty flag
        this.text = this.getTextFromNBT(tag);

        super.handleMessageFromServer(tag);
    }

    @Override
    public void handleMessageFromClient(NBTTagCompound tag) {
        // server side, we do care about dirty flag
        this.setText(this.getTextFromNBT(tag));

        super.handleMessageFromClient(tag);
    }

    @Nullable
    private String getTextFromNBT(NBTTagCompound tag) {
        return (tag.hasKey(NBT_TEXT_KEY, Constants.NBT.TAG_STRING))
            ? tag.getString(NBT_TEXT_KEY)
            : null;
    }

    @SideOnly(Side.CLIENT)
    public static NBTTagCompound getActionNBT( @Nullable String newText) {
        NBTTagCompound data = new NBTTagCompound();
        if (newText != null) {
            data.setString(NBT_TEXT_KEY, newText);
        }
        return data;
    }
}
