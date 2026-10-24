package zone.moddev.mc.lib.api;

import net.minecraft.world.level.block.Block;
import zone.moddev.mc.lib.common.builders.BlockBuilder;
import zone.moddev.mc.lib.common.types.MMDBlock;
import zone.moddev.mc.lib.common.types.MMDMaterial;

public interface BlockTypeFactory<B extends Block> {
    Class<B> blockClass();
    BlockBuilder template(MMDMaterial material);
    B create(MMDMaterial material, MMDBlock definition);
}
