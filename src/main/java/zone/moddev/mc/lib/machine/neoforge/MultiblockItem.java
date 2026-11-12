package zone.moddev.mc.lib.machine.neoforge;

import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;

import zone.moddev.mc.lib.machine.multiblock.MultiblockDefinition;

/**
 * One item places the complete fixed machine. Member blocks should have no
 * independent loot.
 */
public class MultiblockItem extends Item {
    private final Supplier<MultiblockDefinition> definition;
    public MultiblockItem(Properties properties,
        Supplier<MultiblockDefinition> definition) {
        super(properties);
        this.definition = definition;
    }
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide)
            return InteractionResult.SUCCESS;
        var result = MultiblockPlacement.place(new BlockPlaceContext(context),
            definition.get());
        if (!result.success()) {
            if (context.getPlayer() != null)
                context.getPlayer().displayClientMessage(
                    Component.literal(result.diagnostic()), true);
            return InteractionResult.FAIL;
        }
        if (context.getPlayer() != null && !context.getPlayer().isCreative())
            context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
