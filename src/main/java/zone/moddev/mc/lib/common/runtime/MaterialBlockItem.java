package zone.moddev.mc.lib.common.runtime;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;

import zone.moddev.mc.lib.common.types.MMDMaterial;

/** A material block's item; burns for the material's {@code block_burn_time} (0 by default). */
public class MaterialBlockItem extends BlockItem implements MaterialBacked {
  private final MMDMaterial material;
  private final int burn;

  /** The item of {@code block}. */
  public MaterialBlockItem(Block block, Properties properties, MMDMaterial material) {
    super(block, properties);
    this.material = material;
    this.burn = new MaterialStats(material).integer("block_burn_time", 0, 0);
  }

  @Override
  public MMDMaterial material() {
    return material;
  }

  @Override
  public int getBurnTime(ItemStack stack, RecipeType<?> recipe) {
    return burn;
  }
}
