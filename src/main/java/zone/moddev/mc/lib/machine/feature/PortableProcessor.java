package zone.moddev.mc.lib.machine.feature;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** Optional lifecycle policy for a machine whose item carries owned content. */
public interface PortableProcessor {
    ItemStack take();
    void placed(ItemStack stack);
    boolean hasContent(ItemStack stack);
    default void creativeDestroy() {
    }
    default InteractionResult use(Player player, boolean secondary) {
        return InteractionResult.PASS;
    }
    default InteractionResult useItem(UseOnContext context) {
        return InteractionResult.PASS;
    }
    default void tooltip(ItemStack stack, List<Component> lines) {
    }
}
