package zone.moddev.mc.lib.common.fluid;

import java.util.function.Supplier;

import com.mojang.logging.LogUtils;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import org.slf4j.Logger;

import zone.moddev.mc.lib.MMDLib;

/**
 * Lets vanilla glass bottles hold any fluid in {@link #BOTTLEABLE}, without a
 * mixin:
 *
 * <ul>
 * <li>Right-clicking a source block of such a fluid with a glass bottle gives a
 * {@link FluidBottleItem} and leaves the source in place, as water bottles do.
 * <li>Glass bottles and filled bottles get a fluid capability, so tanks and
 * pipes fill and empty them. If another mod already gives glass bottles one,
 * MMDLib logs that and leaves glass bottles to it.
 * </ul>
 *
 * Vanilla's own fluids (water, lava) are always left to vanilla, tagged or not.
 *
 * <p>
 * Experience fluids ({@code #c:experience}, whatever mod adds them) are a
 * special case: a glass bottle takes {@link #EXPERIENCE_CAPACITY} of one and
 * becomes vanilla's Bottle o' Enchanting, no {@link #BOTTLEABLE} tag needed.
 * That is one way only: the XP comes back out of a thrown bottle, not out of
 * the item.
 */
public final class FluidBottles {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Source fluids glass bottles can hold. MMDLib ships it empty: packs and
     * mods fill it.
     */
    public static final TagKey<Fluid> BOTTLEABLE = TagKey.create(
        Registries.FLUID,
        ResourceLocation.fromNamespaceAndPath(MMDLib.MODID, "bottleable"));

    /** Millibuckets in a bottle: a third of a bucket, the common convention. */
    public static final int CAPACITY = 333;

    /**
     * Millibuckets of experience fluid in a Bottle o' Enchanting: the
     * {@link Tags.Fluids#EXPERIENCE} convention.
     */
    public static final int EXPERIENCE_CAPACITY = 250;

    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister
        .createDataComponents(Registries.DATA_COMPONENT_TYPE, MMDLib.MODID);

    /** The fluid a {@link FluidBottleItem} holds. */
    public static final Supplier<DataComponentType<SimpleFluidContent>> CONTENT = COMPONENTS
        .registerComponentType("bottle_fluid",
            builder -> builder.persistent(SimpleFluidContent.CODEC)
                .networkSynchronized(SimpleFluidContent.STREAM_CODEC));

    /** {@code mmdlib:fluid_bottle}. */
    public static final DeferredHolder<net.minecraft.world.item.Item, FluidBottleItem> BOTTLE = MMDLib.ITEMS
        .register("fluid_bottle", () -> new FluidBottleItem(
            new net.minecraft.world.item.Item.Properties()));

    private FluidBottles() {
    }

    /**
     * Registers the component, the capabilities and the bottle-filling hook.
     */
    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        // Lowest priority, so mods registering at the usual priority are seen
        // first.
        modBus.addListener(EventPriority.LOWEST, FluidBottles::capabilities);
        NeoForge.EVENT_BUS.addListener(FluidBottles::fillFromWorld);
    }

    /**
     * Whether glass bottles can hold {@code fluid}: tagged, and not one of
     * vanilla's.
     */
    public static boolean bottleable(Fluid fluid) {
        return !BuiltInRegistries.FLUID.getKey(fluid).getNamespace()
            .equals("minecraft") && fluid.defaultFluidState().is(BOTTLEABLE);
    }

    /**
     * Whether {@code fluid} is an experience fluid, which bottles into a Bottle
     * o' Enchanting.
     */
    public static boolean experience(Fluid fluid) {
        return fluid.defaultFluidState().is(Tags.Fluids.EXPERIENCE);
    }

    /**
     * Millibuckets a glass bottle takes of {@code fluid}, or 0 if it can't hold
     * it.
     */
    public static int capacity(Fluid fluid) {
        if (experience(fluid))
            return EXPERIENCE_CAPACITY;
        return bottleable(fluid) ? CAPACITY : 0;
    }

    /**
     * What a glass bottle full of {@code fluid} becomes: a Bottle o' Enchanting
     * for experience, a {@link FluidBottleItem} otherwise. Only meaningful when
     * {@link #capacity} is non-zero.
     */
    public static ItemStack filled(Fluid fluid) {
        return experience(fluid)
            ? new ItemStack(Items.EXPERIENCE_BOTTLE)
            : FluidBottleItem.filled(fluid);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.FluidHandler.ITEM,
            (stack, context) -> new FluidHandlerItemStack.SwapEmpty(CONTENT,
                stack,
                new net.minecraft.world.item.ItemStack(Items.GLASS_BOTTLE),
                CAPACITY) {
                @Override
                public boolean canFillFluidType(
                    net.neoforged.neoforge.fluids.FluidStack fluid) {
                    return bottleable(fluid.getFluid())
                        && !experience(fluid.getFluid());
                }
            }, BOTTLE.get());
        if (event.isItemRegistered(Capabilities.FluidHandler.ITEM,
            Items.GLASS_BOTTLE)) {
            LOGGER.info(
                "Another mod already gives glass bottles a fluid capability; MMDLib leaves filling them"
                    + " from tanks to it. Right-clicking bottleable fluids still works.");
            return;
        }
        event.registerItem(Capabilities.FluidHandler.ITEM,
            (stack, context) -> new EmptyBottleFluidHandler(stack),
            Items.GLASS_BOTTLE);
    }

    /**
     * A glass bottle used on a bottleable or experience source fills, both
     * sides, before vanilla's bottle handling (which only knows water).
     * Anything else goes on to vanilla.
     */
    private static void fillFromWorld(
        PlayerInteractEvent.RightClickItem event) {
        var held = event.getItemStack();
        if (!held.is(Items.GLASS_BOTTLE))
            return;
        var player = event.getEntity();
        var level = event.getLevel();
        var hit = player.pick(player.blockInteractionRange(), 0, true);
        if (hit.getType() != HitResult.Type.BLOCK
            || !(hit instanceof BlockHitResult block))
            return;
        var pos = block.getBlockPos();
        var fluid = level.getFluidState(pos);
        if (!fluid.isSource() || capacity(fluid.getType()) == 0
            || !level.mayInteract(player, pos))
            return;
        level.playSound(player, player.getX(), player.getY(), player.getZ(),
            SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0F, 1.0F);
        player.awardStat(Stats.ITEM_USED.get(Items.GLASS_BOTTLE));
        player.setItemInHand(event.getHand(), ItemUtils.createFilledResult(held,
            player, filled(fluid.getType())));
        event.setCanceled(true);
        event.setCancellationResult(
            InteractionResult.sidedSuccess(level.isClientSide()));
    }
}
