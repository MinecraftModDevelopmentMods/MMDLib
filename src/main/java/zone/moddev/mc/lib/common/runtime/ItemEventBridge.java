package zone.moddev.mc.lib.common.runtime;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import zone.moddev.mc.lib.api.MaterialEvent;
import zone.moddev.mc.lib.api.MaterialEventContexts.*;
import zone.moddev.mc.lib.api.MaterialEventTypes;
import zone.moddev.mc.lib.api.MaterialEvents;
import zone.moddev.mc.lib.common.types.MMDBlock;
import zone.moddev.mc.lib.common.types.MMDItem;
import zone.moddev.mc.lib.common.types.MMDMaterial;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Dispatches event bindings from the game bus for any registered item or block that has an MMDLib
 * definition, regardless of its Java class. The definition's own binding wins; otherwise its material's
 * binding applies. Click events fire on both logical sides (handlers that change the world must check the side);
 * harvest and projectile events are server only. NeoForge events are never cancelled here.
 */
public final class ItemEventBridge {
    /** Slots checked for player-tick: worn armor and both hands. */
    private static final List<EquipmentSlot> TICK_SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);
    private final MaterialEvents events;
    private final Function<ResourceLocation, MMDItem> definitions;
    private final Function<ResourceLocation, MMDMaterial> materials;
    private final Function<ResourceLocation, MMDBlock> blockDefinitions;
    private final Function<ResourceLocation, MMDMaterial> blockMaterials;

    /** Each function maps a registry ID to its definition/material, or null when it has none. */
    public ItemEventBridge(MaterialEvents events, Function<ResourceLocation, MMDItem> definitions,
            Function<ResourceLocation, MMDMaterial> materials, Function<ResourceLocation, MMDBlock> blockDefinitions,
            Function<ResourceLocation, MMDMaterial> blockMaterials) {
        this.events = Objects.requireNonNull(events); this.definitions = Objects.requireNonNull(definitions);
        this.materials = Objects.requireNonNull(materials); this.blockDefinitions = Objects.requireNonNull(blockDefinitions);
        this.blockMaterials = Objects.requireNonNull(blockMaterials);
    }

    public void register(IEventBus bus) {
        bus.addListener(this::rightClickBlock); bus.addListener(this::rightClickItem); bus.addListener(this::interactEntity);
        bus.addListener(this::leftClickBlock); bus.addListener(this::leftClickEmpty); bus.addListener(this::attackEntity);
        bus.addListener(this::blockDrops); bus.addListener(this::playerTick);
        bus.addListener(this::projectileDamage); bus.addListener(this::projectileHit);
    }

    /**
     * The clicked block's binding runs first, as vanilla uses the block before the held item; it is skipped
     * when sneaking with something in hand, as vanilla does. A consumed block click cancels the interaction
     * as handled, so the held item's binding and vanilla item use do not run.
     */
    private void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var level = event.getLevel(); var stack = event.getItemStack(); var player = event.getEntity();
        var state = level.getBlockState(event.getPos());
        boolean holding = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
        if (!(player.isSecondaryUseActive() && holding)) {
            var block = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            var definition = blockDefinitions.apply(block);
            var id = definition == null ? null : binding(definition.events(), blockMaterials.apply(block),
                    MaterialEventTypes.BLOCK_RIGHT_CLICK.name());
            if (id != null) {
                var use = new BlockUse(level, event.getPos(), state, event.getHitVec().getDirection(), event.getHand(), stack);
                events.dispatch(id, MaterialEventTypes.BLOCK_RIGHT_CLICK, new BlockClick(player, use));
                if (use.consumed()) {
                    event.setCanceled(true);
                    event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide));
                    return;
                }
            }
        }
        dispatch(stack, MaterialEventTypes.TOOL_RIGHT_CLICK, new ToolUse(event.getEntity(), new BlockTarget(level, event.getPos(),
                level.getBlockState(event.getPos()), event.getHitVec().getDirection(), event.getHand(), stack)));
    }
    private void rightClickItem(PlayerInteractEvent.RightClickItem event) {
        dispatch(event.getItemStack(), MaterialEventTypes.TOOL_RIGHT_CLICK,
                new ToolUse(event.getEntity(), new AirTarget(event.getHand(), event.getItemStack())));
    }
    private void interactEntity(PlayerInteractEvent.EntityInteract event) {
        dispatch(event.getItemStack(), MaterialEventTypes.TOOL_RIGHT_CLICK,
                new ToolUse(event.getEntity(), new EntityTarget(event.getTarget(), event.getHand(), event.getItemStack())));
    }
    private void leftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        // Only the initial click; continued mining and aborts are not new actions.
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START || event.getFace() == null) return;
        var level = event.getLevel(); var stack = event.getItemStack();
        dispatch(stack, MaterialEventTypes.TOOL_LEFT_CLICK, new ToolUse(event.getEntity(), new BlockTarget(level, event.getPos(),
                level.getBlockState(event.getPos()), event.getFace(), event.getHand(), stack)));
    }
    /** Client-only: vanilla sends nothing to the server for a left click on air. */
    private void leftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        dispatch(event.getItemStack(), MaterialEventTypes.TOOL_LEFT_CLICK,
                new ToolUse(event.getEntity(), new AirTarget(event.getHand(), event.getItemStack())));
    }
    private void attackEntity(AttackEntityEvent event) {
        var player = event.getEntity(); var stack = player.getMainHandItem();
        dispatch(stack, MaterialEventTypes.TOOL_LEFT_CLICK,
                new ToolUse(player, new EntityTarget(event.getTarget(), InteractionHand.MAIN_HAND, stack)));
        if (event.getTarget() instanceof LivingEntity target)
            dispatch(stack, MaterialEventTypes.WEAPON_HIT, new WeaponUse(player, target));
    }
    /** Server only; NeoForge fires this before the drops are spawned. */
    private void blockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player player)) return;
        dispatch(event.getTool(), MaterialEventTypes.TOOL_HARVEST, new ToolHarvest(player,
                new Harvest(event.getLevel(), event.getPos(), event.getState(), event.getTool(), event.getDrops())));
    }

    /**
     * Both sides. Each bound callback runs once per tick however many worn/held items share it, so a
     * material binding is a per-material effect; the handler can count equipped pieces itself.
     */
    private void playerTick(PlayerTickEvent.Post event) {
        var player = event.getEntity();
        var callbacks = new LinkedHashSet<ResourceLocation>();
        for (var slot : TICK_SLOTS) {
            var id = resolve(player.getItemBySlot(slot), MaterialEventTypes.PLAYER_TICK);
            if (id != null) callbacks.add(id);
        }
        for (var id : callbacks) events.dispatch(id, MaterialEventTypes.PLAYER_TICK, player);
    }
    /** Server only, before mitigation. */
    private void projectileDamage(LivingIncomingDamageEvent event) {
        var target = event.getEntity();
        if (target.level().isClientSide || !(event.getSource().getDirectEntity() instanceof Projectile projectile)) return;
        var ammo = ammunition(projectile);
        dispatch(ammo, MaterialEventTypes.PROJECTILE_DAMAGE, new ProjectileDamage(projectile, target, ammo, event.getContainer()));
    }
    /** Server only, after damage was dealt. */
    private void projectileHit(LivingDamageEvent.Post event) {
        var target = event.getEntity();
        if (target.level().isClientSide || !(event.getSource().getDirectEntity() instanceof Projectile projectile)) return;
        var ammo = ammunition(projectile);
        dispatch(ammo, MaterialEventTypes.PROJECTILE_HIT, new ProjectileHit(projectile, target, ammo));
    }
    /** Arrows keep the stack they were fired from; thrown items expose theirs. Others have none. */
    private static ItemStack ammunition(Projectile projectile) {
        if (projectile instanceof AbstractArrow arrow) return arrow.getPickupItemStackOrigin();
        if (projectile instanceof ItemSupplier supplier) return supplier.getItem();
        return ItemStack.EMPTY;
    }

    private <C> void dispatch(ItemStack stack, MaterialEvent<C> event, C context) {
        var id = resolve(stack, event);
        if (id != null) events.dispatch(id, event, context);
    }
    /** The item definition's binding, else its material's, else null. */
    private ResourceLocation resolve(ItemStack stack, MaterialEvent<?> event) {
        if (stack.isEmpty()) return null;
        var item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        var definition = definitions.apply(item);
        return definition == null ? null : binding(definition.events(), materials.apply(item), event.name());
    }
    /** Resolution rule: the definition's own binding wins over its material's; null when neither binds the event. */
    public static ResourceLocation binding(java.util.Map<String, ResourceLocation> definition, MMDMaterial material, String event) {
        var id = definition.get(event);
        if (id != null || material == null) return id;
        return material.events().get(event);
    }
}
