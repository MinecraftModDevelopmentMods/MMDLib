package zone.moddev.mc.mmdlib.integration.plugins.tinkers.traits;

import javax.annotation.Nonnull;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import slimeknights.tconstruct.library.traits.AbstractTrait;
import slimeknights.tconstruct.library.utils.ToolHelper;

/**
 * <h2><u>Sparkly Tool Modifier:</u></h2>
 * <b>Name:</b> sparkly
 * <br>
 * <b>Desc:</b> Repairs a held, damaged tool on the server by
 * one durability point every 200 world ticks (10 seconds).
 *
 * <br>
 * <b>String Reference:<br></b>
 * Registry names:<br>
 * "sparkly"<br>
 * "mmd-sparkly"<br>
 * {@code mmd-sparkly}
 *
 * @author Java doc author: Vase of Petunias
 */
public class TraitSparkly extends AbstractTrait {

	/**
	 * <b>Units of game ticks:</b> 200 ticks (10 seconds)
	 */
	private static final int REGEN_INTERVAL = 200;

	public TraitSparkly() {
		super("mmd-sparkly", TextFormatting.OBFUSCATED);
	}

	@Override
	public void onUpdate(@Nonnull final ItemStack tool, @Nonnull final World world,
			@Nonnull final Entity entity, @Nonnull final int itemSlot,
			@Nonnull final boolean isHeld) {
		if (!world.isRemote && isHeld && tool.isItemDamaged()
				&& ((world.getTotalWorldTime() % REGEN_INTERVAL) == 0)) {
			ToolHelper.healTool(tool, 1, (EntityLivingBase) entity);
		}
	}
}
