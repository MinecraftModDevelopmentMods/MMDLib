package zone.moddev.mc.lib.smoke;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.common.runtime.*;

/** Injected into a dedicated development run only; excluded from the released JAR. */
@EventBusSubscriber(modid = "mmdlib")
public final class RuntimeSmoke {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        var server = event.getServer();
        try {
            var level = server.overworld(); var player = FakePlayerFactory.getMinecraft(level);
            int placed = 0, projectiles = 0, fluids = 0;
            if (MMDLib.loadedMaterials().size() != 15) throw new AssertionError("Missing materials");
            var detector = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("mmdlib:human_detector"));
            if (MMDLib.standaloneBlocks().size() != 1 || !(detector instanceof zone.moddev.mc.lib.common.runtime.StandardBlocks.DetectorPressurePlate))
                throw new AssertionError("Standalone player-only plate missing: " + detector);
            for (var registrar : RuntimeContentRegistrar.instances()) {
                if (registrar.items().size() != 234) throw new AssertionError("Wrong runtime item count: " + registrar.items().size());
                for (var entry : registrar.blocks().entrySet()) {
                    var itemHolder = registrar.items().get(entry.getKey());
                    if (itemHolder == null || !(itemHolder.get() instanceof BlockItem item)) continue;
                    var pos = new BlockPos((placed % 8) * 4, level.getMaxBuildHeight() - 12, (placed / 8) * 4);
                    level.getChunkAt(pos);
                    level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                    level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(pos.above(2), Blocks.AIR.defaultBlockState(), 3);
                    player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + 2.5);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
                    var hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, .5, 0), Direction.UP, pos, false);
                    var result = item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
                    if (!result.consumesAction() || level.getBlockState(pos.above()).getBlock() != entry.getValue().get())
                        throw new AssertionError("Placement failed: " + entry.getKey() + " result " + result);
                    placed++;
                }
                for (var entry : registrar.projectiles().entrySet()) {
                    var ammo = (MaterialAmmoItem) registrar.items().get(entry.getKey()).get();
                    var stack = new ItemStack(ammo, 12); stack.set(DataComponents.CUSTOM_NAME, Component.literal("Recovery smoke test"));
                    var projectile = ammo.createArrow(level, stack, player, null);
                    if (projectile.pickup != AbstractArrow.Pickup.ALLOWED || projectile.getType() != entry.getValue().get()) throw new AssertionError("Wrong projectile type/pickup");
                    checkStack(stack.copyWithCount(1), projectile.getPickupItemStackOrigin());
                    if (stack.getCount() != 12) throw new AssertionError("Ammunition consumed during construction");
                    var tag = projectile.saveWithoutId(new CompoundTag());
                    var restored = entry.getValue().get().create(level); restored.load(tag);
                    checkStack(stack.copyWithCount(1), restored.getPickupItemStackOrigin());
                    projectiles++;
                }
                for (var family : registrar.fluidFamilies()) {
                    var pos = new BlockPos((fluids % 8) * 4, level.getMaxBuildHeight() - 12, 48 + (fluids / 8) * 4);
                    level.getChunkAt(pos); level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    var bucket = family.bucket.get(); var stack = new ItemStack(bucket);
                    if (!bucket.emptyContents(player, level, pos, null, stack)) throw new AssertionError("Bucket placement failed: " + family.definition.name());
                    var state = level.getBlockState(pos);
                    if (level.getFluidState(pos).getType() != family.source.get()) throw new AssertionError("Wrong fluid placed");
                    var recovered = ((BucketPickup) state.getBlock()).pickupBlock(player, level, pos, state);
                    if (recovered.getItem() != bucket) throw new AssertionError("Wrong fluid bucket recovered");
                    fluids++;
                }
                // Exercise an actual dropped-item crushing recipe, not just recipe decoding.
                var floor = level.getSharedSpawnPos().above(8);
                level.getChunkAt(floor); level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
                level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(floor.above()).inflate(2)).forEach(net.minecraft.world.entity.Entity::discard);
                var input = new net.minecraft.world.entity.item.ItemEntity(level, floor.getX() + .5, floor.getY() + 1.1, floor.getZ() + .5, new ItemStack(Items.IRON_INGOT, 2));
                level.addFreshEntity(input);
                var hammer = new ItemStack(registrar.items().get(ResourceLocation.parse("mmdlib:iron_crackhammer")).get());
                player.getAbilities().instabuild = false; player.setShiftKeyDown(false);
                player.setItemInHand(InteractionHand.MAIN_HAND, hammer);
                // Aim horizontally at a floating drop: there is no clicked block
                // to accidentally exercise only the old useOn path.
                player.setPos(input.getX(), input.getY() + .1 - player.getEyeHeight(), input.getZ() - 2);
                player.setYRot(0); player.setXRot(0);
                // Crushing is a data-bound tool-right-click handler, so fire the real NeoForge event the bridge listens to.
                net.neoforged.neoforge.common.CommonHooks.onItemRightClick(player, InteractionHand.MAIN_HAND);
                var hammerResult = "event";
                var dust = registrar.items().get(ResourceLocation.parse("mmdlib:iron_dust")).get();
                var outputs = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(floor.above()), entity -> entity.getItem().getItem() == dust);
                // The default server config crushes up to 8 per click, 1 wear each: both ingots go.
                if (input.isAlive() || outputs.isEmpty() || hammer.getDamageValue() != 2)
                    throw new AssertionError("Crackhammer failed: result=" + hammerResult + ", input=" + input.getItem().getCount() + ", outputs=" + outputs.size() + ", wear=" + hammer.getDamageValue() + ", recipes=" + level.getRecipeManager().getAllRecipesFor(zone.moddev.mc.lib.common.recipes.CrushingRecipe.TYPE.get()).size());
                var emerald = new ItemStack(registrar.items().get(ResourceLocation.parse("mmdlib:emerald_pickaxe")).get());
                var quartz = new ItemStack(registrar.items().get(ResourceLocation.parse("mmdlib:quartz_pickaxe")).get());
                if (!emerald.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()) || quartz.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()))
                    throw new AssertionError("Incorrect tag-based mining tiers");
                var helmet = new ItemStack(registrar.items().get(ResourceLocation.parse("mmdlib:emerald_helmet")).get());
                if (helmet.get(DataComponents.DYED_COLOR) != null) throw new AssertionError("Precolored armor must not be tinted twice");
                var texture = ((ArmorItem) helmet.getItem()).getMaterial().value().layers().getFirst().texture(false);
                if (!texture.getNamespace().equals("mmdlib")) throw new AssertionError("Missing custom armor layer");
                try (var stream = RuntimeSmoke.class.getResourceAsStream("/assets/" + texture.getNamespace() + "/" + texture.getPath())) {
                    if (stream == null) throw new AssertionError("Armor texture not packaged: " + texture);
                }
                var horse = (AnimalArmorItem) registrar.items().get(ResourceLocation.parse("mmdlib:emerald_horse_armor")).get();
                try (var stream = RuntimeSmoke.class.getResourceAsStream("/assets/" + horse.getTexture().getNamespace() + "/" + horse.getTexture().getPath())) {
                    if (stream == null) throw new AssertionError("Horse armor texture not packaged: " + horse.getTexture());
                }
            }
            if (projectiles != 14 || fluids != 15 || placed != 59) throw new AssertionError("Wrong smoke counts: " + placed + "/" + projectiles + "/" + fluids);
            MMDLib.LOGGER.info("MMDLIB_RUNTIME_SMOKE_PASS: {} blocks placed; {} arrow/bolt component-preserving save round trips; {} buckets placed/recovered; tag-based tiers, authored armor textures and dropped-item crushing verified", placed, projectiles, fluids);
        } catch (Throwable ex) {
            MMDLib.LOGGER.error("MMDLIB_RUNTIME_SMOKE_FAIL", ex);
        } finally { server.halt(false); }
    }
    private static void checkStack(ItemStack expected, ItemStack actual) {
        if (actual.getCount() != 1 || !ItemStack.isSameItemSameComponents(expected, actual)) throw new AssertionError("Wrong projectile recovery stack");
    }
}
