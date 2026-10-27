package zone.moddev.mc.lib.api;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import zone.moddev.mc.lib.common.runtime.MaterialBlockItem;
import zone.moddev.mc.lib.common.types.MMDBlock;
import zone.moddev.mc.lib.common.types.MMDMaterial;
import zone.moddev.mc.lib.common.types.MaterialPart;

public interface BlockImplementation<B extends Block> {
    Class<B> blockClass();
    B create(MMDMaterial material, MMDBlock definition);
    default B create(MMDMaterial material, MMDBlock definition, zone.moddev.mc.lib.common.types.FactoryData data) {
        if (!data.equals(zone.moddev.mc.lib.common.types.FactoryData.EMPTY))
            throw new IllegalArgumentException("Implementation must explicitly decode its factory_data");
        return create(material, definition);
    }
    /** Called by the runtime registrar; override to use the prepared properties, block set type, or base block. */
    default B create(BlockContext context) { return create(context.material(), context.definition(), context.factoryData()); }
    /** Decode {@code factory_data} eagerly so malformed module data fails at load instead of registry construction. */
    default void validate(MaterialPart part) {}
    /** The block's item, when its definition has one; the default burns for the material's {@code block_burn_time}. */
    default Item createItem(Block block, ItemContext context) { return new MaterialBlockItem(block, context.properties(), context.material()); }
}
