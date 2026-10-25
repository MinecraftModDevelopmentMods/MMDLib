package zone.moddev.mc.lib.api;

import net.minecraft.world.item.Item;
import zone.moddev.mc.lib.common.types.MMDItem;
import zone.moddev.mc.lib.common.types.MMDMaterial;

/** Java behavior implementation selected by a data module's factory ID. */
public interface ItemImplementation<I extends Item> {
    Class<I> itemClass();
    I create(MMDMaterial material, MMDItem definition);
    default I create(MMDMaterial material, MMDItem definition, zone.moddev.mc.lib.common.types.FactoryData data) {
        if (!data.equals(zone.moddev.mc.lib.common.types.FactoryData.EMPTY))
            throw new IllegalArgumentException("Implementation must explicitly decode its factory_data");
        return create(material, definition);
    }
}
