package zone.moddev.mc.lib.common.runtime;

import java.util.List;

import zone.moddev.mc.lib.api.MaterialEventContexts.AirTarget;
import zone.moddev.mc.lib.api.MaterialEventContexts.BlockTarget;
import zone.moddev.mc.lib.api.MaterialEventContexts.EntityTarget;
import zone.moddev.mc.lib.api.MaterialEventContexts.Harvest;
import zone.moddev.mc.lib.api.MaterialEventContexts.ToolTarget;
import zone.moddev.mc.lib.MMDLibConfig;
import zone.moddev.mc.lib.common.recipes.CrushingRecipe;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Crackhammer behavior, bound as {@code mmdlib:crackhammer_crush} (right-click) and
 * {@code mmdlib:crackhammer_harvest} (mining) by {@code mmdlib/extension/crackhammer.json}
 * and referenced from the crackhammer item module.
 * Dispatched on both sides by the item event bridge; only the server processes drops.
 */
public final class CrackhammerEvents {
    private CrackhammerEvents() {}

    /** Handler IDs declared in {@code mmdlib/extension/crackhammer.json}; an item bound to either is a crackhammer. */
    public static final net.minecraft.resources.ResourceLocation CRUSH =
            net.minecraft.resources.ResourceLocation.parse("mmdlib:crackhammer_crush");
    public static final net.minecraft.resources.ResourceLocation HARVEST =
            net.minecraft.resources.ResourceLocation.parse("mmdlib:crackhammer_harvest");

    public static void crush(Player player, ToolTarget target) {
        Level level = player.level();
        if (level.isClientSide) return;
        List<ItemEntity> entities = switch (target) {
            case BlockTarget block -> block.face() == Direction.UP
                    ? level.getEntitiesOfClass(ItemEntity.class, new AABB(block.position().above()),
                            entity -> entity.isAlive() && !entity.getItem().isEmpty())
                    : List.of();
            case EntityTarget entity -> entity.entity() instanceof ItemEntity item
                    && item.isAlive() && !item.getItem().isEmpty() ? List.of(item) : List.of();
            case AirTarget air -> itemsInSight(level, player);
        };
        crush(level, player, target.hand(), target.stack(), entities);
    }

    /**
     * Mining: drops with a crushing recipe are replaced by their crushed output. Each crushed item costs
     * 1 durability on top of the block break, limited by what the hammer has left; the rest drop uncrushed.
     */
    public static void harvest(Player player, Harvest harvest) {
        // The event's tool is a pre-mining copy; wear goes to the held hammer. If mining broke it, nothing is crushed.
        ItemStack hammer = player.getMainHandItem();
        if (!ItemStack.isSameItem(hammer, harvest.tool())) return;
        var level = harvest.level();
        int budget = hammer.isDamageableItem() ? Math.max(0, hammer.getMaxDamage() - hammer.getDamageValue()) : Integer.MAX_VALUE;
        // Ores are crushed whole, without fortune. Ores that drop gems are left alone, so the hammer
        // is no better than a pickaxe for mining gems; gems are crushed only by right-click.
        if (budget > 0 && harvest.state().is(net.neoforged.neoforge.common.Tags.Blocks.ORES) && !harvest.drops().isEmpty()
                && harvest.drops().stream().noneMatch(drop -> isGem(drop.getItem()))) {
            ItemStack ore = crushResult(level, hammer, new ItemStack(harvest.state().getBlock()));
            if (!ore.isEmpty()) {
                ItemEntity first = harvest.drops().getFirst();
                ItemEntity entity = new ItemEntity(level, first.getX(), first.getY(), first.getZ(), ore);
                entity.setDeltaMovement(first.getDeltaMovement());
                entity.setDefaultPickUpDelay();
                harvest.drops().clear();
                harvest.drops().add(entity);
                hammer.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                return;
            }
        }
        int used = 0;
        var crushed = new java.util.ArrayList<ItemEntity>();
        for (var drops = harvest.drops().iterator(); drops.hasNext() && used < budget;) {
            ItemEntity drop = drops.next();
            ItemStack input = drop.getItem();
            if (isGem(input)) continue;
            ItemStack output = crushResult(level, hammer, input);
            if (output.isEmpty()) continue;
            int operations = Math.min(input.getCount(), budget - used);
            used += operations;
            if (operations == input.getCount()) drops.remove();
            else { input.shrink(operations); drop.setItem(input); }
            int remaining = Math.multiplyExact(output.getCount(), operations);
            while (remaining > 0) {
                ItemStack stack = output.copy();
                stack.setCount(Math.min(stack.getMaxStackSize(), remaining));
                remaining -= stack.getCount();
                ItemEntity entity = new ItemEntity(level, drop.getX(), drop.getY(), drop.getZ(), stack);
                entity.setDeltaMovement(drop.getDeltaMovement());
                entity.setDefaultPickUpDelay();
                crushed.add(entity);
            }
        }
        // Added after the scan so crushed outputs are never crushed again in the same harvest.
        harvest.drops().addAll(crushed);
        if (used > 0) hammer.hurtAndBreak(used, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
    }

    /** One operation's output, or empty when there is no recipe or the hammer's tier cannot crush the input. */
    private static ItemStack crushResult(Level level, ItemStack hammer, ItemStack input) {
        if (input.isEmpty() || !canCrushDroppedBlock(hammer, input)) return ItemStack.EMPTY;
        var recipeInput = new SingleRecipeInput(input);
        return level.getRecipeManager().getRecipeFor(CrushingRecipe.TYPE.get(), recipeInput, level)
                .map(recipe -> recipe.value().assemble(recipeInput, level.registryAccess())).orElse(ItemStack.EMPTY);
    }

    private static List<ItemEntity> itemsInSight(Level level, Player player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
        // Item entities are not picked by vanilla's entity interaction ray. Stop
        // our own ray at terrain so dropped items cannot be crushed through walls.
        end = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getLocation();
        final Vec3 target = end;
        List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class,
                new AABB(start, end).inflate(.25), entity -> entity.isAlive()
                        && !entity.getItem().isEmpty()
                        && entity.getBoundingBox().inflate(.15).clip(start, target).isPresent());
        entities.sort(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(start)));
        return entities;
    }

    private static void crush(Level level, Player player, InteractionHand hand,
            ItemStack hammer, List<ItemEntity> entities) {
        for (ItemEntity entity : entities) {
            ItemStack input = entity.getItem();
            ItemStack output = crushResult(level, hammer, input);
            if (output.isEmpty()) continue;
            // Sneaking is the per-use full-stack control; ordinary use crushes
            // the server-configured count.
            int requested = player.isShiftKeyDown() ? input.getCount()
                    : Math.min(input.getCount(), MMDLibConfig.CRACKHAMMER_CRUSH_COUNT.getAsInt());
            int durability = hammer.isDamageableItem()
                    ? Math.max(0, hammer.getMaxDamage() - hammer.getDamageValue())
                    : requested;
            int operations = Math.min(requested, durability);
            if (operations == 0) return;
            input.shrink(operations);
            if (input.isEmpty()) entity.discard(); else entity.setItem(input);
            spawnOutputs(level, entity, output, operations);
            hammer.hurtAndBreak(operations, player,
                    LivingEntity.getSlotForHand(hand));
            level.playSound(null, entity.blockPosition(),
                    net.minecraft.sounds.SoundEvents.GRAVEL_BREAK,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.5F,
                    0.5F + (level.random.nextFloat() * 0.3F));
            return;
        }
    }

    /** A gem is any item tagged {@code gems} or {@code gems/...} in any namespace, e.g. {@code #c:gems/diamond}. */
    private static boolean isGem(ItemStack stack) {
        return stack.getTags().anyMatch(tag -> {
            String path = tag.location().getPath();
            return path.equals("gems") || path.startsWith("gems/");
        });
    }

    private static boolean canCrushDroppedBlock(ItemStack hammer, ItemStack input) {
        if (!(input.getItem() instanceof BlockItem blockItem)) return true;
        return canHarvest(hammer, blockItem.getBlock().defaultBlockState());
    }

    private static boolean canHarvest(ItemStack hammer, BlockState state) {
        return !state.requiresCorrectToolForDrops() || hammer.isCorrectToolForDrops(state);
    }

    private static void spawnOutputs(Level level, ItemEntity source, ItemStack result, int operations) {
        int remaining = Math.multiplyExact(result.getCount(), operations);
        while (remaining > 0) {
            ItemStack output = result.copy();
            output.setCount(Math.min(output.getMaxStackSize(), remaining));
            remaining -= output.getCount();
            ItemEntity crushed = new ItemEntity(level, source.getX(), source.getY(), source.getZ(), output);
            crushed.setDefaultPickUpDelay();
            level.addFreshEntity(crushed);
        }
    }
}
