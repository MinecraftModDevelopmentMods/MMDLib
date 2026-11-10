package zone.moddev.mc.companion.test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.items.*;

import zone.moddev.mc.companion.CompanionMod;
import zone.moddev.mc.lib.api.automation.*;
import zone.moddev.mc.lib.api.capabilities.IoMode;

/**
 * Test-only counters prove steady-state work is skipped and invalidation is
 * observed.
 */
@EventBusSubscriber(modid = CompanionMod.ID, bus = EventBusSubscriber.Bus.MOD)
public final class TestAutomationCapabilities {
    public static final BlockCapability<IItemHandler, Direction> ITEMS = BlockCapability
        .createSided(
            ResourceLocation.parse("mmdlib_companion:test_counted_items"),
            IItemHandler.class);
    public static final Map<BlockPos, Integer> LOOKUPS = new HashMap<>();
    public static final AtomicInteger SPECIFICATIONS = new AtomicInteger();

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(ITEMS, BlockEntityType.CHEST,
            (chest, side) -> {
                LOOKUPS.merge(chest.getBlockPos(), 1, Integer::sum);
                return new ItemStackHandler(1);
            });
        event.registerItem(AddonCapabilities.ADDON,
            (stack, context) -> item -> {
                SPECIFICATIONS.incrementAndGet();
                return new AddonSpec(
                    ResourceLocation.parse("mmdlib_companion:test_puller"),
                    ResourceType.ITEMS, IoMode.INPUT, 8, 20, 4, false);
            }, Items.BLAZE_ROD);
    }
}
