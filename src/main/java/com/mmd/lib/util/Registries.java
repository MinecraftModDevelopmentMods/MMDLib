package com.mmd.lib.util;

import com.mmd.lib.common.types.MMDBlock;
import com.mmd.lib.common.types.MMDItem;
import com.mmd.lib.common.types.MMDMaterial;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class Registries {
    private static final ResourceKey<Registry<Registry<?>>> MMD_REGISTRIES_KEY = createKey("mmdlib:registries");
    public static final ResourceKey<Registry<MMDMaterial>> MATERIALS_KEY =  createKey("mmdlib:materials");
    public static final ResourceKey<Registry<MMDItem>> ITEMS_KEY =  createKey("mmdlib:items");
    public static final ResourceKey<Registry<MMDBlock>> BLOCKS_KEY =  createKey("mmdlib:blocks");

    private static final MappedRegistry<Registry<?>> MMD_REGISTRIES = new MappedRegistry<>(MMD_REGISTRIES_KEY, Lifecycle.experimental(), null);
    public static final Registry<MMDMaterial> MATERIALS_REGISTRY = makeRegistry(MATERIALS_KEY);
    public static final Registry<MMDItem> ITEMS_REGISTRY = makeRegistry(ITEMS_KEY);
    public static final Registry<MMDBlock> BLOCKS_REGISTRY = makeRegistry(BLOCKS_KEY);

    private static <T> ResourceKey<Registry<T>> createKey(final String name) {
        return ResourceKey.createRegistryKey(new ResourceLocation(name));
    }

    private static <T> Registry<T> makeRegistry(ResourceKey<Registry<T>> key) {
        MappedRegistry<T> registry = new MappedRegistry<>(key, Lifecycle.experimental(), null);
        return Registry.register(MMD_REGISTRIES, key.location().toString(), registry);
    }
}
