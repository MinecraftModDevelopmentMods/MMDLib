package zone.moddev.mc.mmdlib.integration.plugins.tinkers.traits;

import javax.annotation.Nonnull;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import slimeknights.tconstruct.library.traits.AbstractTrait;

/**
 * <h2><u>Soft Tool Modifier:</u></h2>
 * <b>Name:</b> soft
 * <br>
 * <b>Desc:</b> Adds a durability penalty when the tool takes damage, based on
 * 125% of the damage passed to the hook, rounded down to an integer.
 *
 * <br>
 * <b>String Reference:<br></b>
 * Registry names:<br>
 * "soft"<br>
 * "mmd-soft"<br>
 * {@code mmd-soft}
 *
 * @author Daniel Hazelton &lt;dshadowwolf@gmail.com&gt;
 * @author Java doc author: Vase of Petunias
 */
public class TraitSoft extends AbstractTrait {

	public TraitSoft() {
		super("mmd-soft", 0xffffff);
	}

	@Override
	public int onToolDamage(@Nonnull final ItemStack tool, @Nonnull final int damage,
			@Nonnull final int newDamage, @Nonnull final EntityLivingBase entity) {
		return super.onToolDamage(tool, damage, newDamage + ((int) (damage * 1.25f)), entity);
	}
}
