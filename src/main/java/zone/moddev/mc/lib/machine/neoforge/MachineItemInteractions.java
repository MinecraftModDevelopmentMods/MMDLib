package zone.moddev.mc.lib.machine.neoforge;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.capabilities.Capabilities;

import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.machine.feature.ItemReceiver;
import zone.moddev.mc.lib.machine.multiblock.LinkedMember;
import zone.moddev.mc.lib.machine.multiblock.MultiblockController;

/**
 * Shared right-click routing and held-stack/remainder ownership for machine
 * interactions.
 */
public final class MachineItemInteractions {
    private MachineItemInteractions() {
    }

    private static BlockEntity owner(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (!level.hasChunkAt(pos))
            return null;
        var structure = MultiblockMembers.resolve(level, pos);
        if (structure != null)
            return structure.owner();
        var entity = level.getBlockEntity(pos);
        if (entity instanceof LinkedMember member) {
            // Clients receive the formation identity and links, not the server
            // manifest.
            var link = member.memberLink();
            if (level.isClientSide() && link != null
                && link.dimension().equals(level.dimension().location())
                && level.hasChunkAt(link.controller())) {
                var controller = level.getBlockEntity(link.controller());
                if (controller instanceof ScreenHost host
                    && host.screenInteractionAnchor(pos))
                    return controller;
            }
            return null; // Never route stale or unloaded membership to a
                         // different formation.
        }
        return entity;
    }

    private static ItemReceiver receiver(BlockEntity owner,
        UseOnContext context, ItemStack offered) {
        ItemReceiver receiver = owner instanceof ItemReceiver custom
            && custom.accepts(offered.copy()) ? custom : null;
        if (receiver == null && owner instanceof DefinedMachineEntity machine) {
            var processor = machine.screenFeatures()
                .findProcessor(machine.definition.processor(),
                    ItemReceiver.class)
                .orElse(null);
            if (processor != null && processor.accepts(offered.copy()))
                receiver = processor;
        }
        if (receiver == null) {
            // XML machine manual filling intentionally includes static tanks
            // without automation groups.
            var fluids = owner instanceof DefinedMachineEntity machine
                && machine.fluids.getTanks() > 0
                    ? machine.fluids
                    : context.getLevel().getCapability(
                        Capabilities.FluidHandler.BLOCK,
                        context.getClickedPos(), context.getClickedFace());
            if (fluids == null && context.getLevel().isClientSide()
                && owner instanceof ScreenHost host
                && host.screenFluids() != null
                && host.screenFluids().getTanks() > 0)
                fluids = host.screenFluids(); // Prediction only; the server
                                              // still requires the clicked port
                                              // capability.
            if (fluids != null) {
                var candidate = new FluidItemReceiver(fluids);
                if (candidate.accepts(offered))
                    receiver = candidate;
            }
        }
        return receiver;
    }

    /**
     * Let recognized exchanges use the block path even while sneaking; respect
     * explicit denials.
     */
    public static void secondaryUse(
        net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isSecondaryUseActive()
            || !event.getUseBlock().isDefault() || event.getUseItem().isFalse())
            return;
        var block = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (!(block instanceof DefinedMachineBlock)
            && !(block instanceof MultiblockBlock))
            return;
        var context = new UseOnContext(event.getEntity(), event.getHand(),
            event.getHitVec());
        var owner = owner(context);
        if (owner != null && !context.getItemInHand().isEmpty()
            && ((owner instanceof DefinedMachineEntity machine
                && machine.portable() != null
                && context.getItemInHand().is(MachineContent.PICKUP_TOOLS))
                || receiver(owner, context,
                    context.getItemInHand().copyWithCount(1)) != null))
            event.setUseBlock(net.neoforged.neoforge.common.util.TriState.TRUE);
    }

    public static InteractionResult interact(UseOnContext context) {
        var player = context.getPlayer();
        var held = context.getItemInHand();
        if (player == null || held.isEmpty())
            return InteractionResult.PASS;
        var owner = owner(context);
        if (owner == null || owner.isRemoved())
            return InteractionResult.PASS;
        var offered = held.copyWithCount(1);
        var receiver = receiver(owner, context, offered);
        if (receiver == null)
            return InteractionResult.PASS;
        if (context.getLevel().isClientSide())
            return InteractionResult.SUCCESS; // Recognition only; no client
                                              // inventory/tank changes.
        if (owner instanceof MultiblockController controller
            && controller.multiblock() != null
            && !controller.multiblock().ready())
            return InteractionResult.FAIL;
        int offeredCount = Math.clamp(receiver.offerLimit(held.copy()), 1,
            held.getCount());
        var result = receiver.receive(held.copyWithCount(offeredCount),
            context);
        if (!result.accepted())
            return InteractionResult.FAIL;
        if (!player.isCreative() || result.consumeInCreative()) {
            held.shrink(offeredCount);
            var remainder = result.remainder();
            if (!held.isEmpty()
                && ItemStack.isSameItemSameComponents(held, remainder)) {
                int merged = Math.min(remainder.getCount(),
                    Math.max(0, held.getMaxStackSize() - held.getCount()));
                held.grow(merged);
                remainder.shrink(merged);
            }
            // Replace the hand even for a partial stack: vanilla restores the
            // original stack's
            // count after creative Item.useOn, which must not duplicate unique
            // portable contents.
            player.setItemInHand(context.getHand(),
                held.isEmpty() ? remainder : held.copy());
            if (!held.isEmpty() && !remainder.isEmpty())
                player.getInventory().placeItemBackInInventory(remainder);
        }
        return InteractionResult.CONSUME;
    }

    public static ItemInteractionResult useOnBlock(UseOnContext context) {
        var result = interact(context);
        if (result == InteractionResult.PASS)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        // A recognized but rejected exchange still owns this click. Do not
        // spill a bucket
        // into the world or place the carried machine beside a full receiver.
        return context.getLevel().isClientSide()
            ? ItemInteractionResult.SUCCESS
            : ItemInteractionResult.CONSUME;
    }
}
