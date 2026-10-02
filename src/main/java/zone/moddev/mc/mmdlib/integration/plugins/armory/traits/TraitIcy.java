package zone.moddev.mc.mmdlib.integration.plugins.armory.traits;

import c4.conarm.lib.traits.AbstractArmorTrait;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

/**
 * <h2><u>Icy Armor Modifier</u></h2>
 * <b>Name:</b> icy
 * <br>
 * <b>Desc:</b>
 * Applies Fire Protection
 *
 * <br>
 * <b>String Reference:<br></b>
 * "icy"
 * "mmd-icy"
 */
public class TraitIcy extends AbstractArmorTrait {

    public TraitIcy() {
        super("mmd-icy", TextFormatting.GRAY);
    }

    @Override
    public float onDamaged(ItemStack armor, EntityPlayer player, DamageSource source, float damage, float newDamage, LivingDamageEvent evt) {
        float newNewDamage = newDamage;
        if(source.isFireDamage() ){

            newNewDamage = 0f;
        }
        return newNewDamage;
    }
}
