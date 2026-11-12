package zone.moddev.mc.lib.smoke;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;

import com.mojang.serialization.JsonOps;

import io.netty.buffer.Unpooled;

import net.minecraft.core.HolderSet;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.machine.ingredient.FluidAmount;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.neoforge.StorageGroups;
import zone.moddev.mc.lib.machine.recipe.CyclePlan;
import zone.moddev.mc.lib.machine.recipe.MachineProduct;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;
import zone.moddev.mc.lib.machine.recipe.RecipeMatcher;

/**
 * Real item/fluid catalysts, including allocation, capacity, persistence and
 * recipe transport.
 */
final class ReusableInputSmoke {
    private static final ResourceLocation ID = ResourceLocation
        .parse("example:reusable");

    private ReusableInputSmoke() {
    }

    private static final class Storage {
        final ItemStorage items = new ItemStorage(2);
        final FluidStorage fluids = new FluidStorage();
        final MachineInventory inventory;

        Storage() {
            items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            items.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT));
            fluids.addTank(1000, Role.INPUT);
            fluids.tank(0).fill(new FluidStack(Fluids.WATER, 1000),
                FluidAction.EXECUTE);
            inventory = MachineInventory.builder()
                .group("items", StorageGroups.items(items, 0, 1))
                .group("water", StorageGroups.fluids(fluids, 0)).build();
        }

        boolean unplanned() {
            return items.plan(0).isNone() && items.plan(1).isNone()
                && fluids.plan(0).isNone();
        }
    }

    static void check(ServerLevel level) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        for (String resource : List.of(
            "\"items\":{\"item\":\"minecraft:iron_ingot\"}",
            "\"fluids\":\"minecraft:water\",\"amount\":1000")) {
            for (boolean consumed : List.of(false, true)) {
                var json = JsonParser.parseString(
                    "{" + resource + ",\"consumed\":" + consumed + "}");
                var part = MachineIngredient.CODEC.parse(ops, json)
                    .getOrThrow();
                require(part.consumed() == consumed, "JSON consumption flag");
                var encoded = MachineIngredient.CODEC.encodeStart(ops, part)
                    .getOrThrow();
                require(MachineIngredient.CODEC.parse(ops, encoded).getOrThrow()
                    .consumed() == consumed, "JSON round trip");
                var wire = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                    level.registryAccess());
                try {
                    MachineIngredient.STREAM_CODEC.encode(wire, part);
                    var decoded = MachineIngredient.STREAM_CODEC.decode(wire);
                    require(
                        decoded.consumed() == consumed
                            && decoded.amount() == part.amount(),
                        "wire round trip");
                    require(!wire.isReadable(),
                        "wire consumed exactly one ingredient");
                } finally {
                    wire.release();
                }
            }
            require(
                MachineIngredient.CODEC
                    .parse(ops, JsonParser.parseString("{" + resource + "}"))
                    .getOrThrow().consumed(),
                "old recipes default to consumed");
        }
        require(
            MachineIngredient.CODEC
                .parse(ops,
                    JsonParser
                        .parseString("{\"energy\":20,\"consumed\":false}"))
                .error().isPresent(),
            "reusable energy rejected");
        require(MachineIngredient.CODEC.parse(ops, JsonParser.parseString(
            "{\"items\":{\"item\":\"minecraft:iron_ingot\"},\"consumed\":\"no\"}"))
            .error().isPresent(), "malformed consumption flag rejected");

        var water = HolderSet.direct(Fluids.WATER.builtInRegistryHolder());
        var mixed = new MachineRecipe(Map.of("items", List.of(
            new MachineIngredient.ItemPart(
                Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 1, false),
            new MachineIngredient.ItemPart(Ingredient.of(Items.IRON_INGOT), 1)),
            "water",
            List.of(new MachineIngredient.FluidPart(water, 750, false),
                new MachineIngredient.FluidPart(water, 250))),
            Map.of("items", List.of(new MachineProduct.ItemProduct(
                new ItemStack(Items.GOLD_INGOT), 1))),
            10);
        var storage = new Storage();
        require(RecipeMatcher.check(mixed, storage.inventory).ok(),
            "mixed recipe matches");
        var plan = CyclePlan.of(ID, mixed, storage.inventory).orElseThrow();
        require(
            plan.items().stream().filter(take -> take.retained() > 0)
                .allMatch(take -> take.stack().is(Items.GOLD_INGOT)),
            "broad catalyst moves off iron");
        require(plan.fluids().getFirst().retained() == 750,
            "mixed fluid shares tracked");
        require(plan.reserve(storage.inventory), "mixed inputs reserve");
        require(
            storage.items.freeToTake(0) == 0
                && storage.items.freeToTake(1) == 0,
            "both consumed and retained items locked");
        require(storage.fluids.drain(1000, FluidAction.EXECUTE).isEmpty(),
            "all required fluid locked");
        require(CyclePlan.of(ID, mixed, storage.inventory).isEmpty(),
            "another cycle cannot reuse locks");

        var nbt = RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
        var saved = CyclePlan.CODEC.encodeStart(nbt, plan).getOrThrow();
        var restored = CyclePlan.CODEC.parse(nbt, saved).getOrThrow();
        var loaded = new Storage();
        require(restored.restore(loaded.inventory),
            "saved reservations restored");
        require(restored.finish(loaded.inventory, RandomSource.create(0)),
            "restored cycle completes");
        require(loaded.fluids.tank(0).getFluidAmount() == 750,
            "only 250 mB consumed");
        require(
            loaded.items.getStackInSlot(0).isEmpty()
                && loaded.items.getStackInSlot(1).is(Items.GOLD_INGOT)
                && loaded.items.getStackInSlot(1).getCount() == 2,
            "gold catalyst kept and product made");
        require(loaded.unplanned(), "finish releases every reservation");
        var missing = new Storage();
        missing.items.setStackInSlot(1, ItemStack.EMPTY);
        require(!restored.restore(missing.inventory) && missing.unplanned(),
            "missing catalyst rejects restore");
        plan.release(storage.inventory);
        require(storage.unplanned() && storage.items.freeToTake(1) == 1,
            "pause/release unlocks catalysts");
        require(RecipeMatcher.consume(mixed, storage.inventory),
            "immediate consumption succeeds");
        require(
            storage.items.getStackInSlot(0).isEmpty()
                && storage.items.getStackInSlot(1).is(Items.GOLD_INGOT)
                && storage.fluids.tank(0).getFluidAmount() == 750,
            "immediate consumption preserves catalysts");

        var reusable = MachineRecipeSmoke.recipe(ops,
            """
                {"inputs":{"water":[{"fluids":"minecraft:water","amount":1000,"consumed":false}],
                           "items":[{"items":{"item":"minecraft:gold_ingot"},"consumed":false}]},"time":10}
                """);
        var full = new Storage();
        for (int i = 0; i < 3; i++) {
            var cycle = CyclePlan.of(ID, reusable, full.inventory)
                .orElseThrow();
            require(
                cycle.reserve(full.inventory)
                    && cycle.finish(full.inventory, RandomSource.create(0)),
                "full tank catalyst cycles repeatedly");
            require(full.fluids.tank(0).getFluidAmount() == 1000
                && full.items.getStackInSlot(1).getCount() == 1
                && full.unplanned(), "no loss or duplication");
        }
        // Retained water cannot be replaced by lava or make room for additional
        // water.
        for (var fluid : List.of(Fluids.WATER, Fluids.LAVA)) {
            var product = new MachineProduct.FluidProduct(
                new FluidAmount(fluid.builtInRegistryHolder(),
                    net.minecraft.core.component.DataComponentPatch.EMPTY, 1),
                1);
            var blocked = new MachineRecipe(reusable.inputs(),
                Map.of("water", List.of(product)), 10);
            require(
                !RecipeMatcher.check(blocked, full.inventory).ok()
                    && CyclePlan.of(ID, blocked, full.inventory).isEmpty(),
                "retained fluid occupies capacity");
        }
        full.items.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 64));
        full.items.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 64));
        var blockedItems = new MachineRecipe(reusable.inputs(),
            Map.of("items", List.of(new MachineProduct.ItemProduct(
                new ItemStack(Items.GOLD_INGOT), 1))),
            10);
        require(
            !RecipeMatcher.check(blockedItems, full.inventory).ok()
                && CyclePlan.of(ID, blockedItems, full.inventory).isEmpty(),
            "retained items occupy capacity");
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new AssertionError("Reusable inputs: " + message);
    }
}
