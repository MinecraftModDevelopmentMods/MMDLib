package zone.moddev.mc.lib;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;

import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.api.MaterialRegistration;
import zone.moddev.mc.lib.common.factories.MaterialFactory;
import zone.moddev.mc.lib.common.loading.DefinitionLoader;
import zone.moddev.mc.lib.common.runtime.ItemEventBridge;
import zone.moddev.mc.lib.common.types.MMDItem;
import zone.moddev.mc.lib.common.types.MMDMaterial;

@Mod(MMDLib.MODID)
public class MMDLib {
    public static final String MODID = "mmdlib";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);
    private final zone.moddev.mc.lib.common.runtime.RuntimeContentRegistrar runtime;
    private static volatile Map<ResourceLocation, MaterialFactory.Result> loadedMaterials = Map.of();
    private static volatile Map<ResourceLocation, MMDItem> itemDefinitions = Map.of();
    private static volatile Map<ResourceLocation, MMDMaterial> itemMaterials = Map.of();

    /** Complete after deferred construction, before definition registry events. */
    public static Map<ResourceLocation, MaterialFactory.Result> loadedMaterials() { return loadedMaterials; }

    public MMDLib(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, MMDLibConfig.SERVER_SPEC);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        runtime = new zone.moddev.mc.lib.common.runtime.RuntimeContentRegistrar(MODID, MaterialExtensions.GLOBAL, modBus);
        zone.moddev.mc.lib.common.recipes.CrushingRecipe.TYPES.register(modBus);
        zone.moddev.mc.lib.common.recipes.CrushingRecipe.SERIALIZERS.register(modBus);
        modBus.addListener(zone.moddev.mc.lib.util.Registries::register);
        modBus.addListener(this::publishDefinitions);
        modBus.addListener((FMLConstructModEvent event) -> event.enqueueWork(this::loadDefinitions));
        new ItemEventBridge(MaterialExtensions.GLOBAL.events(), id -> itemDefinitions.get(id), id -> itemMaterials.get(id))
                .register(NeoForge.EVENT_BUS);
    }

    private void loadDefinitions() {
        var roots = new ArrayList<DefinitionLoader.SourceRoot>();
        for (var info : ModList.get().getModFiles()) {
            var file = info.getFile();
            var root = file.findResource("mmdlib");
            if (!Files.isDirectory(root) || info.getMods().isEmpty()) continue;
            // A root-level directory belongs to the first mod declared by this physical mod file.
            var namespace = info.getMods().getFirst().getModId();
            var module = FMLLoader.getGameLayer().findModule(info.moduleName()).orElseThrow(() ->
                    new IllegalStateException("Cannot find module for definitions in " + file.getFileName()));
            roots.add(new DefinitionLoader.SourceRoot(namespace, root, module.getClassLoader()));
        }
        var loaded = DefinitionLoader.load(roots, MaterialExtensions.GLOBAL, id -> ModList.get().isLoaded(id));
        var definitions = new java.util.HashMap<ResourceLocation, MMDItem>();
        var materials = new java.util.HashMap<ResourceLocation, MMDMaterial>();
        loaded.materials().values().forEach(result -> result.content().items().values().forEach(item -> {
            definitions.put(item.name(), item); materials.put(item.name(), result.material());
        }));
        itemDefinitions = Map.copyOf(definitions);
        itemMaterials = Map.copyOf(materials);
        loadedMaterials = loaded.materials();
        loadedMaterials.values().stream().filter(result -> result.material().name().orElseThrow().getNamespace().equals(MODID)).forEach(runtime::queue);
        LOGGER.info("Loaded MMDLib definitions: {} item modules, {} block modules, {} fluid modules, {} extension files, {} materials",
                loaded.modules().items().size(), loaded.modules().blocks().size(), loaded.modules().fluids().size(),
                loaded.extensions().size(), loadedMaterials.size());
    }

    private void publishDefinitions(RegisterEvent event) {
        MaterialRegistration.publishCallbacks(event, MaterialExtensions.GLOBAL);
        loadedMaterials.values().forEach(result -> MaterialRegistration.publishDefinitions(event, result));
    }
}
