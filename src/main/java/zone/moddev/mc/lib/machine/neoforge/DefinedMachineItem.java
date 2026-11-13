package zone.moddev.mc.lib.machine.neoforge;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;

import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.feature.PortableProcessor;

/** Generic item lifecycle delegates only to explicitly portable processors. */
public final class DefinedMachineItem extends BlockItem {
    private final PortableProcessor behavior;

    public DefinedMachineItem(DefinedMachineBlock block,
        MachineDefinition definition) {
        super(block,
            new Properties().stacksTo(definition.behavior().stackSize()));
        behavior = definition.behavior().portable()
            ? (PortableProcessor) definition.behavior().processor()
                .create(definition.processor(), null)
            : null;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var exchange = MachineItemInteractions.interact(context);
        if (exchange != InteractionResult.PASS)
            return exchange;
        if (behavior != null) {
            var result = behavior.useItem(context);
            if (result != InteractionResult.PASS)
                return result;
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        var payload = context.getItemInHand().copy();
        var result = super.place(context);
        if (behavior != null && result.consumesAction()
            && !context.getLevel().isClientSide()
            && context.getLevel().getBlockEntity(
                context.getClickedPos()) instanceof DefinedMachineEntity host
            && host.portable() != null) {
            host.portable().placed(payload);
            if (context.getPlayer() != null && context.getPlayer().isCreative()
                && behavior.hasContent(payload))
                context.getItemInHand().shrink(1);
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
        List<Component> tooltip, TooltipFlag flags) {
        super.appendHoverText(stack, context, tooltip, flags);
        if (behavior != null)
            behavior.tooltip(stack, tooltip);
    }
}
