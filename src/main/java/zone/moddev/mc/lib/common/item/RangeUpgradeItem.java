package zone.moddev.mc.lib.common.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Permanent range module; installed sockets, not stack counts, determine its
 * effect.
 */
public class RangeUpgradeItem extends Item {
    public RangeUpgradeItem() {
        super(new Properties());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
        List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.mmdlib.range_upgrade.effect")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.mmdlib.range_upgrade.limit")
            .withStyle(ChatFormatting.GRAY));
    }
}
