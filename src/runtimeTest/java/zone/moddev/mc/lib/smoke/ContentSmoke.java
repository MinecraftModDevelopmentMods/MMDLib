package zone.moddev.mc.lib.smoke;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.common.recipes.CrushingRecipe;
import zone.moddev.mc.lib.common.runtime.*;

/**
 * Checks material content registered through MMDLib's own registrar on a
 * running server: registration, block placement, ammunition recovery, buckets,
 * crushing, mining tiers and armor layers. MMDLib ships no materials; the five
 * used here (iron, copper, gold, emerald, quartz) are test resources.
 */
final class ContentSmoke {
    private ContentSmoke() {
    }

    /**
     * The materials (five from test resources, two from the test scripts), the
     * standalone blocks (the player-only plate, NAND gate and hook block from
     * test resources; the hook block from the test script) and the item count.
     */
    static void registered() {
        if (MMDLib.loadedMaterials().size() != 7)
            throw new AssertionError("Missing materials");
        var detector = BuiltInRegistries.BLOCK
            .get(ResourceLocation.parse("mmdlib:test_player_plate"));
        if (MMDLib.standaloneBlocks().size() != 12
            || !(detector instanceof StandardBlocks.DetectorPressurePlate))
            throw new AssertionError(
                "Standalone player-only plate missing: " + detector);
        // From test resources the five materials' items, the player-only
        // plate's, gate's and hook block's block items and the hook tool;
        // from the test script the hook block's block item, the hook tool, the
        // hammer, the phase 2 material tool, and seven phase 3 entries; the
        // buckets of the standalone fluids test/syrup (test resources),
        // test/chocolate and test/plasma (test script).
        if (registrar().items().size() != 169)
            throw new AssertionError(
                "Wrong runtime item count: " + registrar().items().size());
    }

    /** Every block item places its block, one at a time on {@code floor}. */
    static void blocksPlace(ServerLevel level, BlockPos floor) {
        var player = FakePlayerFactory.getMinecraft(level);
        int placed = 0;
        for (var entry : registrar().blocks().entrySet()) {
            var itemHolder = registrar().items().get(entry.getKey());
            if (itemHolder == null
                || !(itemHolder.get() instanceof BlockItem item))
                continue;
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), 3);
            player.setPos(floor.getX() + .5, floor.getY() + 1,
                floor.getZ() + 2.5);
            player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(item));
            var hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0),
                Direction.UP, floor, false);
            var result = item.useOn(
                new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            if (!result.consumesAction() || level.getBlockState(floor.above())
                .getBlock() != entry.getValue().get())
                throw new AssertionError("Placement failed: " + entry.getKey()
                    + " result " + result);
            placed++;
        }
        // The test materials' blocks, the release detector plate, the test
        // gate, the hook block and the script hook block.
        if (placed != 50)
            throw new AssertionError("Wrong placed block count: " + placed);
    }

    /**
     * Arrows and bolts keep their pickup item and its components across save
     * and load.
     */
    static void ammunitionRecovers(ServerLevel level) {
        var player = FakePlayerFactory.getMinecraft(level);
        int projectiles = 0;
        for (var entry : registrar().projectiles().entrySet()) {
            var ammo = (MaterialAmmoItem) registrar().items()
                .get(entry.getKey()).get();
            var stack = new ItemStack(ammo, 12);
            stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("Recovery smoke test"));
            var projectile = ammo.createArrow(level, stack, player, null);
            if (projectile.pickup != AbstractArrow.Pickup.ALLOWED
                || projectile.getType() != entry.getValue().get())
                throw new AssertionError("Wrong projectile type/pickup");
            checkStack(stack.copyWithCount(1),
                projectile.getPickupItemStackOrigin());
            if (stack.getCount() != 12)
                throw new AssertionError(
                    "Ammunition consumed during construction");
            var tag = projectile.saveWithoutId(new CompoundTag());
            var restored = entry.getValue().get().create(level);
            restored.load(tag);
            checkStack(stack.copyWithCount(1),
                restored.getPickupItemStackOrigin());
            projectiles++;
        }
        // An arrow and a bolt for each of the five test materials.
        if (projectiles != 10)
            throw new AssertionError("Wrong projectile count: " + projectiles);
    }

    /**
     * Every bucket places its fluid and picks it back up, one at a time on
     * {@code floor}.
     */
    static void bucketsPlaceAndRecover(ServerLevel level, BlockPos floor) {
        var player = FakePlayerFactory.getMinecraft(level);
        int fluids = 0;
        for (var family : registrar().fluidFamilies()) {
            if (family.block == null || family.bucket == null)
                continue; // A bucket-only fluid cannot be placed.
            var pos = floor.above();
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            var bucket = family.bucket.get();
            var stack = new ItemStack(bucket);
            if (!bucket.emptyContents(player, level, pos, null, stack))
                throw new AssertionError(
                    "Bucket placement failed: " + family.definition.name());
            var state = level.getBlockState(pos);
            if (level.getFluidState(pos).getType() != family.source.get())
                throw new AssertionError("Wrong fluid placed");
            var recovered = ((BucketPickup) state.getBlock())
                .pickupBlock(player, level, pos, state);
            if (recovered.getItem() != bucket)
                throw new AssertionError("Wrong fluid bucket recovered");
            fluids++;
        }
        // One molten fluid for each of the five test materials, and the
        // standalone test/syrup and test/chocolate; test/plasma has no block.
        if (fluids != 7)
            throw new AssertionError("Wrong bucket count: " + fluids);
    }

    /** A real dropped-item crushing recipe, not just recipe decoding. */
    static void crackhammerCrushesDrops(ServerLevel level, BlockPos floor) {
        var player = FakePlayerFactory.getMinecraft(level);
        level.getChunkAt(floor);
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        level.getEntitiesOfClass(ItemEntity.class,
            new AABB(floor.above()).inflate(2)).forEach(Entity::discard);
        var input = new ItemEntity(level, floor.getX() + .5, floor.getY() + 1.1,
            floor.getZ() + .5, new ItemStack(Items.IRON_INGOT, 2));
        level.addFreshEntity(input);
        var hammer = new ItemStack(item("mmdlib:iron_crackhammer"));
        player.getAbilities().instabuild = false;
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, hammer);
        // Aim horizontally at a floating drop: there is no clicked block
        // to accidentally exercise only the old useOn path.
        player.setPos(input.getX(), input.getY() + .1 - player.getEyeHeight(),
            input.getZ() - 2);
        player.setYRot(0);
        player.setXRot(0);
        // Crushing is a data-bound tool-right-click handler, so fire the real
        // NeoForge event the
        // bridge listens to.
        CommonHooks.onItemRightClick(player, InteractionHand.MAIN_HAND);
        var dust = item("mmdlib:iron_dust");
        var outputs = level.getEntitiesOfClass(ItemEntity.class,
            new AABB(floor.above()),
            entity -> entity.getItem().getItem() == dust);
        // The default server config crushes up to 8 per click, 1 wear each:
        // both ingots go.
        if (input.isAlive() || outputs.isEmpty()
            || hammer.getDamageValue() != 2)
            throw new AssertionError("Crackhammer failed: input="
                + input.getItem().getCount() + ", outputs=" + outputs.size()
                + ", wear=" + hammer.getDamageValue() + ", recipes="
                + level.getRecipeManager()
                    .getAllRecipesFor(CrushingRecipe.TYPE.get()).size());
    }

    /**
     * Tag-based mining tiers, and armor that uses its own layer textures
     * instead of a tint. MMDLib ships no armor art, so only the texture
     * locations are checked, not that the files exist.
     */
    static void toolsAndArmor() {
        var emerald = new ItemStack(item("mmdlib:emerald_pickaxe"));
        var quartz = new ItemStack(item("mmdlib:quartz_pickaxe"));
        if (!emerald.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState())
            || quartz
                .isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()))
            throw new AssertionError("Incorrect tag-based mining tiers");
        var helmet = new ItemStack(item("mmdlib:emerald_helmet"));
        if (helmet.get(DataComponents.DYED_COLOR) != null)
            throw new AssertionError(
                "Precolored armor must not be tinted twice");
        var texture = ((ArmorItem) helmet.getItem()).getMaterial().value()
            .layers().getFirst().texture(false);
        if (!texture.getNamespace().equals("mmdlib"))
            throw new AssertionError("Missing custom armor layer");
        var horse = (AnimalArmorItem) item("mmdlib:emerald_horse_armor");
        if (!horse.getTexture().getNamespace().equals("mmdlib"))
            throw new AssertionError("Missing custom horse armor texture");
        materialTags();
    }

    /** MaterialTagPack tags each material item by its part. */
    private static void materialTags() {
        var expected = java.util.Map.of("mmdlib:emerald_sword",
            List.of(ItemTags.SWORDS, ItemTags.SWORD_ENCHANTABLE,
                ItemTags.SHARP_WEAPON_ENCHANTABLE),
            "mmdlib:quartz_pickaxe",
            List.of(ItemTags.PICKAXES, ItemTags.MINING_ENCHANTABLE),
            "mmdlib:emerald_boots",
            List.of(ItemTags.FOOT_ARMOR_ENCHANTABLE,
                ItemTags.ARMOR_ENCHANTABLE),
            "mmdlib:gold_bow", List.of(ItemTags.BOW_ENCHANTABLE),
            "mmdlib:iron_scythe", List.of(ItemTags.WEAPON_ENCHANTABLE),
            "mmdlib:copper_arrow", List.of(ItemTags.ARROWS,
                ItemTags.create(ResourceLocation.parse("c:arrows"))));
        expected.forEach((id, tags) -> {
            var stack = new ItemStack(item(id));
            for (var tag : tags)
                if (!stack.is(tag))
                    throw new AssertionError(
                        id + " is not in #" + tag.location());
        });
    }

    /** MMDLib's own registrar; the machine smoke registrar is skipped. */
    private static RuntimeContentRegistrar registrar() {
        var own = RuntimeContentRegistrar.instances().stream()
            .filter(registrar -> registrar != MachineSmoke.registrar())
            .toList();
        if (own.size() != 1)
            throw new AssertionError(
                "Expected one MMDLib registrar, got " + own);
        return own.getFirst();
    }

    private static Item item(String id) {
        return registrar().items().get(ResourceLocation.parse(id)).get();
    }

    private static void checkStack(ItemStack expected, ItemStack actual) {
        if (actual.getCount() != 1
            || !ItemStack.isSameItemSameComponents(expected, actual))
            throw new AssertionError("Wrong projectile recovery stack");
    }
}
