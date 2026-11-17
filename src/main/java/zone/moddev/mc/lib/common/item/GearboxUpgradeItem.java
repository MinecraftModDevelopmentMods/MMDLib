package zone.moddev.mc.lib.common.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Permanent processor acceleration, in eighths of the base rate. */
public class GearboxUpgradeItem extends Item {
    public static final int MAX_UPGRADES = 8;

    public GearboxUpgradeItem() {
        super(new Properties().stacksTo(MAX_UPGRADES));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
        List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.mmdlib.gearbox_upgrade.effect")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.mmdlib.gearbox_upgrade.limit")
            .withStyle(ChatFormatting.GRAY));
    }
}
