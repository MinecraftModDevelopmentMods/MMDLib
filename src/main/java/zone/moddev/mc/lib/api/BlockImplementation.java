package zone.moddev.mc.lib.api;

import net.minecraft.world.level.block.Block;
import zone.moddev.mc.lib.common.types.MMDBlock;
import zone.moddev.mc.lib.common.types.MMDMaterial;

public interface BlockImplementation<B extends Block> {
    Class<B> blockClass();
    B create(MMDMaterial material, MMDBlock definition);
    default B create(MMDMaterial material, MMDBlock definition, zone.moddev.mc.lib.common.types.FactoryData data) {
        if (!data.equals(zone.moddev.mc.lib.common.types.FactoryData.EMPTY))
            throw new IllegalArgumentException("Implementation must explicitly decode its factory_data");
        return create(material, definition);
    }
}
