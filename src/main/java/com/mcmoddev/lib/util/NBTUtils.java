package com.mcmoddev.lib.util;

import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import mcp.MethodsReturnNonnullByDefault;

@SuppressWarnings("WeakerAccess")
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

    public static NBTTagCompound getPatch(NBTTagCompound from, NBTTagCompound to) {
        NBTTagCompound patch = new NBTTagCompound();

        Set<String> keys = from.getKeySet();
        for(String key: keys) {
            NBTBase f = from.getTag(key);
            if (!to.hasKey(key)) {
                // TODO: handle stuff existing in FROM but not in TO
            } else {
                NBTBase t = to.getTag(key);
                if (!f.equals(t)) {
                    patch.setTag(key, t);
                }
            }
        }

        return patch;
    }
}
