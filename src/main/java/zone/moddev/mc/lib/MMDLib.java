package zone.moddev.mc.lib;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(MMDLib.MODID)
public class MMDLib {
    public static final String MODID = "mmdlib";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);

    public MMDLib(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        zone.moddev.mc.lib.common.recipes.CrushingRecipe.TYPES.register(modBus);
        zone.moddev.mc.lib.common.recipes.CrushingRecipe.SERIALIZERS.register(modBus);
        modBus.addListener(zone.moddev.mc.lib.util.Registries::register);
        modBus.addListener((net.neoforged.neoforge.registries.RegisterEvent event) ->
                zone.moddev.mc.lib.api.MaterialRegistration.publishCallbacks(event,
                        zone.moddev.mc.lib.api.MaterialExtensions.GLOBAL));
        // Material/template discovery remains an explicit, unfinished loading stage.
    }
}
