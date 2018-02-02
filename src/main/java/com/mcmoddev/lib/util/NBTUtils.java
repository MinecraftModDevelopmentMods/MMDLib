package com.mcmoddev.lib.util;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import mcp.MethodsReturnNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class NBTUtils {
    private NBTUtils() {}

    @Nullable
    public static NBTTagCompound wrapCompound(@Nullable NBTTagCompound inner, String... path) {
        if (inner == null) {
            return null;
        }

        return setInnerCompound(new NBTTagCompound(), inner, path);
    }

    public static NBTTagCompound setInnerCompound(NBTTagCompound root, NBTTagCompound inner, String... path) {
        NBTTagCompound nbt = root;
        for(int index = 0; index < path.length - 1; index++) {
            String key = path[index];
            if (!nbt.hasKey(key, Constants.NBT.TAG_COMPOUND)) {
                nbt.setTag(key, new NBTTagCompound());
            }
            nbt = nbt.getCompoundTag(key);
        }
        nbt.setTag(path[path.length - 1], inner);
        return root;
    }
}
