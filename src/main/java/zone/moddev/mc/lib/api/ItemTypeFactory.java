package zone.moddev.mc.lib.api;

import net.minecraft.world.item.Item;
import zone.moddev.mc.lib.common.builders.ItemBuilder;
import zone.moddev.mc.lib.common.types.MMDItem;
import zone.moddev.mc.lib.common.types.MMDMaterial;

public interface ItemTypeFactory<I extends Item> extends ItemImplementation<I> {
    Class<I> itemClass();
    ItemBuilder template(MMDMaterial material);
    I create(MMDMaterial material, MMDItem definition);
}
