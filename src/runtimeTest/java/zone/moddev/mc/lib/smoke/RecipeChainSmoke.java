package zone.moddev.mc.lib.smoke;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.gui.runtime.ClientMenuItems;
import zone.moddev.mc.lib.machine.feature.RecipeChain;
import zone.moddev.mc.lib.machine.feature.RecipeCycle;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient.EnergyPart;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient.ItemPart;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.neoforge.RecipeOperations;
import zone.moddev.mc.lib.machine.neoforge.StorageGroups;
import zone.moddev.mc.lib.machine.recipe.MachineProduct.ItemProduct;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;
import zone.moddev.mc.lib.machine.recipe.RecipeGraph;

/** Shared runner, priority, concurrent demand and discovery integration. */
final class RecipeChainSmoke {
    private RecipeChainSmoke() {
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.fromNamespaceAndPath("mmdlib_test", value);
    }

    private static MachineRecipe recipe(Item input, int count, Item output,
        int produced, int ticks, long energy) {
        var inputs = new LinkedHashMap<String, List<MachineIngredient>>();
        if (input != null)
            inputs.put("input",
                List.of(new ItemPart(Ingredient.of(input), count)));
        inputs.put("energy", List.of(new EnergyPart(energy)));
        return new MachineRecipe(inputs,
            Map.of("output",
                List.of(new ItemProduct(new ItemStack(output, produced), 1))),
            ticks);
    }

    private static final class Rig {
        final Item[] products = {Items.COBBLESTONE, Items.GRAVEL, Items.SAND,
            Items.GLASS, Items.GLASS_PANE};
        final ItemStorage items = new ItemStorage(5).role(Role.OUTPUT, 0, 1, 2,
            3, 4);
        final EnergyBuffer energy = new EnergyBuffer(100000, 100000);
        final RecipeChain chain;

        Rig() {
            var groups = java.util.stream.IntStream.range(0, 5)
                .mapToObj(i -> StorageGroups.items(items, i)).toList();
            var steps = new ArrayList<RecipeChain.Step>();
            for (int i = 0; i < 5; i++) {
                var inventory = MachineInventory.builder()
                    .group("output", groups.get(i))
                    .group("energy", StorageGroups.energy(energy));
                if (i > 0)
                    inventory.group("input", groups.get(i - 1));
                steps.add(new RecipeChain.Step(id("stage_" + i),
                    recipe(i == 0 ? null : products[i - 1], i == 4 ? 6 : 1,
                        products[i], i == 4 ? 16 : 1, 3, 7),
                    inventory.build()));
            }
            chain = new RecipeChain(steps);
            items.pendingClaims(chain::pendingTake);
            energy.setStored(100000);
        }
    }

    static void runner(ServerLevel level) {
        var rig = new Rig();
        var step = rig.chain.step(1);
        var runner = new RecipeCycle(step.inventory());
        rig.items.restoreStack(0, new ItemStack(Items.COBBLESTONE, 2));
        rig.energy.setStored(0);
        require(runner.start(step.id(), step.recipe()),
            "reserve without power");
        require(runner.progress() == 0 && rig.energy.stored() == 0,
            "reserving does not process or charge");
        require(runner.advance(level.random) == RecipeCycle.Result.STALLED,
            "no power retains active work");
        require(rig.items.extractItem(0, 64, true).getCount() == 1,
            "only unreserved surplus is extractable");
        rig.energy.setStored(7);
        runner.advance(level.random);
        var saved = runner.state();
        var ops = RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
        var encoded = RecipeCycle.State.CODEC.encodeStart(ops, saved)
            .getOrThrow();
        require(
            runner.restore(
                RecipeCycle.State.CODEC.parse(ops, encoded).getOrThrow()),
            "saved runner restores");
        require(runner.restore(saved) && rig.items.plan(0).take() == 1,
            "repeated restore does not accumulate claims");
        require(!runner.start(id("replacement"), step.recipe()),
            "active work cannot be replaced");
        runner.advance(level.random);
        require(
            runner.advance(level.random) == RecipeCycle.Result.COMPLETED
                && rig.energy.stored() == 0
                && rig.items.getStackInSlot(1).is(Items.GRAVEL),
            "exact total energy and one output after three paid ticks");
        require(runner.advance(level.random) == RecipeCycle.Result.IDLE,
            "completion is never repeated");
        blockedCompletion(level);
    }

    private static void blockedCompletion(ServerLevel level) {
        var items = new CrusherSmoke.Capped(2);
        items.role(Role.INPUT, 0).role(Role.OUTPUT, 1);
        items.restoreStack(0, new ItemStack(Items.COBBLESTONE));
        var energy = new EnergyBuffer(10, 10);
        energy.setStored(3);
        var inventory = MachineInventory.builder()
            .group("input", StorageGroups.items(items, 0))
            .group("output", StorageGroups.items(items, 1))
            .group("energy", StorageGroups.energy(energy)).build();
        var runner = new RecipeCycle(inventory);
        require(
            runner.start(id("blocked"),
                recipe(Items.COBBLESTONE, 1, Items.GRAVEL, 1, 1, 3)),
            "blocked-completion fixture reserves");
        items.cap = 0;
        require(runner.advance(level.random) == RecipeCycle.Result.BLOCKED
            && runner.progress() == 1 && energy.stored() == 0
            && items.getStackInSlot(0).getCount() == 1
            && items.getStackInSlot(1).isEmpty() && items.plan(0).take() == 1,
            "subclass extraction failures roll back contents and reservations");
        items.cap = Integer.MAX_VALUE;
        require(runner.advance(level.random) == RecipeCycle.Result.COMPLETED
            && energy.stored() == 0 && items.getStackInSlot(1).is(Items.GRAVEL),
            "blocked completion retries without charging twice");
    }

    static void priority(ServerLevel level) {
        var rig = new Rig();
        rig.chain.target(4, 64);
        rig.items.restoreStack(3, new ItemStack(Items.GLASS, 3));
        var before = rig.items.getStackInSlot(3).copy();
        var hopper = rig.items.view(i -> IoMode.OUTPUT);
        require(
            hopper.extractItem(3, 64, true).isEmpty()
                && hopper.extractItem(3, 64, false).isEmpty(),
            "partial crafting batch protected before machine tick");
        require(
            ItemStack.matches(before, rig.items.getStackInSlot(3))
                && rig.items.reservationPlans().get(3).isNone(),
            "simulation creates neither mutations nor active reservations");
        require(rig.items.plan(3).take() == 3,
            "menu snapshot includes batch claim");
        var mirror = new ClientMenuItems(5, rig.items);
        mirror.setStackInSlot(3, before.copy());
        mirror.setPlan(3, rig.items.plan(3));
        require(mirror.extractItem(3, 64, true).isEmpty(),
            "client click prediction observes the same pending claim");
        rig.chain.target(4, 0);
        require(hopper.extractItem(3, 64, true).getCount() == 3,
            "removed demand immediately releases pending claims");
        rig.chain.target(4, 64);
        rig.chain.paused(true);
        require(hopper.extractItem(3, 64, true).getCount() == 3,
            "manual pause releases pending batch protection");
        rig.chain.paused(false);
        rig.chain.held(true);
        require(hopper.extractItem(3, 64, true).getCount() == 3,
            "host suspension releases pending claims");
        rig.chain.held(false);
        rig.chain.drain();
        require(hopper.extractItem(3, 64, true).getCount() == 3,
            "switch request releases pending claims before ticking");
        rig.chain.cancelDrain();
        rig.items.restoreStack(3, new ItemStack(Items.GLASS, 8));
        require(hopper.extractItem(3, 64, true).getCount() == 2,
            "only the next batch is protected, surplus stays free");
        rig.chain.tick(level.random);
        require(
            rig.chain.cycle(4).plan().isPresent()
                && rig.items.reservationPlans().get(3).take() == 6,
            "processor can consume its own pending batch");
        rig.chain.drain();
        require(!rig.chain.drained(), "drain waits for active cycles");
        rig.chain.paused(true);
        rig.chain.tick(level.random);
        require(!rig.chain.drained() && rig.items.plan(3).take() == 6,
            "pause does not abandon active work during switch");
        rig.chain.paused(false);
        for (int i = 0; i < 10; i++)
            rig.chain.tick(level.random);
        require(
            rig.chain.drained() && rig.items.getStackInSlot(4).getCount() == 16,
            "drain completes existing work without admitting new batches");
        require(rig.items.getStackInSlot(3).getCount() == 2,
            "old path leftovers are preserved");
    }

    static void concurrent(ServerLevel level) {
        var rig = new Rig();
        rig.chain.target(4, 64);
        boolean concurrent = false;
        for (int tick = 0; tick < 800; tick++) {
            rig.chain.tick(level.random);
            int active = 0;
            for (int i = 0; i < 5; i++)
                if (rig.chain.cycle(i).plan().isPresent())
                    active++;
            concurrent |= active > 1;
        }
        require(concurrent, "multiple stages run together");
        require(rig.items.getStackInSlot(4).getCount() == 64,
            "None intermediates still supply six-item crafting batches");
        require(rig.energy.stored() == 99300,
            "24 four-stage glass cycles and four pane batches pay exactly 700 FE");
        long energy = rig.energy.stored();
        for (int tick = 0; tick < 30; tick++)
            rig.chain.tick(level.random);
        require(rig.energy.stored() == energy, "satisfied targets stop work");
        rig.items.extractItem(4, 1, false);
        // A sixteen-item recipe cannot fit in a slot holding 63. Do not void
        // overflow or lock its inputs while the final output is blocked.
        rig.items.restoreStack(3, new ItemStack(Items.GLASS, 6));
        require(rig.chain.pendingTake(3) == 0,
            "blocked output releases pending claims");

        rig.items.restoreStack(3, ItemStack.EMPTY);
        rig.items.restoreStack(4, new ItemStack(Items.DIRT));
        for (int tick = 0; tick < 30; tick++)
            rig.chain.tick(level.random);
        require(
            rig.energy.stored() == energy
                && rig.items.getStackInSlot(0).isEmpty(),
            "blocked final output does not propagate unfillable demand to None stages");
        rig.chain.target(0, 1);
        for (int tick = 0; tick < 30; tick++)
            rig.chain.tick(level.random);
        require(
            rig.items.getStackInSlot(0).getCount() == 1
                && rig.chain.cycle(0).plan().isEmpty(),
            "independent source target still refills while downstream output is blocked");

        var buffered = new Rig();
        buffered.chain.target(3, 6); // One downstream pane batch.
        buffered.chain.bufferLimit(3, 6);
        buffered.chain.target(4, 64);
        buffered.items.restoreStack(2, new ItemStack(Items.SAND, 64));
        buffered.items.restoreStack(3, new ItemStack(Items.GLASS, 6));
        require(buffered.chain.pendingTake(2) == 0,
            "a ready One Run buffer does not reserve more input for a distant Full Stack target");
        buffered.chain.tick(level.random);
        require(
            buffered.chain.cycle(4).plan().isPresent()
                && buffered.chain.cycle(3).plan().isPresent(),
            "consuming the ready batch allows its replacement to run concurrently");

        var saved = new Rig();
        saved.chain.target(4, 64);
        for (int i = 0; i < 12; i++)
            saved.chain.tick(level.random);
        saved.chain.drain();
        var ops = RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
        var encoded = RecipeChain.State.CODEC
            .encodeStart(ops, saved.chain.state()).getOrThrow();
        var copy = new Rig();
        for (int i = 0; i < 5; i++)
            copy.items.restoreStack(i, saved.items.getStackInSlot(i));
        copy.energy.setStored(saved.energy.stored());
        require(
            copy.chain.restore(
                RecipeChain.State.CODEC.parse(ops, encoded).getOrThrow()),
            "concurrent saved plans restore against shared stage storage");
        for (int i = 0; i < 10; i++) {
            saved.chain.tick(level.random);
            copy.chain.tick(level.random);
        }
        require(
            copy.chain.drained()
                && copy.energy.stored() == saved.energy.stored(),
            "pending drain and paid progress survive save/load");
        for (int i = 0; i < 5; i++)
            require(
                ItemStack.matches(copy.items.getStackInSlot(i),
                    saved.items.getStackInSlot(i)),
                "save/load preserves every stage output");
        fairness(level);
        admission(level);
    }

    private static void admission(ServerLevel level) {
        var rig = new Rig();
        rig.chain.target(0, 64);
        rig.chain.target(1, 64);
        rig.chain.tick(level.random);
        require(rig.chain.cycle(0).plan().isPresent(),
            "source starts while admitted");
        for (int tick = 0; tick < 10; tick++)
            rig.chain.tick(level.random, stage -> stage != 0);
        require(
            rig.items.getStackInSlot(1).getCount() == 1
                && rig.chain.cycle(0).plan().isEmpty(),
            "closing source admission lets active work finish and downstream work run");
        rig.chain.tick(level.random);
        require(rig.chain.cycle(0).plan().isPresent(),
            "reopening admission resumes source work");
    }

    private static void fairness(ServerLevel level) {
        var items = new ItemStorage(2).role(Role.OUTPUT, 0, 1);
        var energy = new EnergyBuffer(100, 100);
        var power = StorageGroups.energy(energy);
        var source = StorageGroups.items(items, 0);
        var output = StorageGroups.items(items, 1);
        var chain = new RecipeChain(List.of(
            new RecipeChain.Step(id("costly_source"),
                recipe(null, 0, Items.COBBLESTONE, 1, 1, 10),
                MachineInventory.builder().group("output", source)
                    .group("energy", power).build()),
            new RecipeChain.Step(id("cheap_grind"),
                recipe(Items.COBBLESTONE, 1, Items.GRAVEL, 1, 1, 1),
                MachineInventory.builder().group("input", source)
                    .group("output", output).group("energy", power).build())));
        items.restoreStack(0, new ItemStack(Items.COBBLESTONE, 32));
        chain.target(0, 64);
        chain.target(1, 64);
        chain.tick(level.random);
        for (int i = 0; i < 11; i++) {
            energy.setStored(energy.stored() + 1);
            chain.tick(level.random);
        }
        require(items.getStackInSlot(0).getCount() == 32
            && items.getStackInSlot(1).getCount() == 1 && energy.stored() == 0,
            "scarce FE funds both stages without cheaper work draining the waiting charge");
    }

    static void discovery(ServerLevel level) {
        var cost = new RecipeOperations.Cost(5, 20);
        var panes = level.getRecipeManager()
            .byKey(ResourceLocation.parse("minecraft:glass_pane"))
            .orElseThrow();
        var op = RecipeOperations
            .crafting(panes, new ItemStack(Items.GLASS), level, cost)
            .orElseThrow();
        require(op.inputCount() == 6 && op.output().getCount() == 16,
            "craft adapter preserves the shaped six-glass batch");
        require(RecipeOperations
            .crafting(panes, new ItemStack(Items.SAND), level, cost).isEmpty(),
            "unrelated materials are not invented");
        var named = new ItemStack(Items.GLASS);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Named glass"));
        var exact = RecipeOperations.crafting(panes, named, level, cost)
            .orElseThrow();
        var ingredient = ((ItemPart) exact.recipe().inputs().get("input")
            .getFirst()).items();
        require(
            ingredient.test(named)
                && !ingredient.test(new ItemStack(Items.GLASS)),
            "adapted recipes preserve the concrete source components");
        var mixed = level.getRecipeManager()
            .byKey(ResourceLocation.parse("minecraft:stone_pickaxe"))
            .orElseThrow();
        require(RecipeOperations
            .crafting(mixed, new ItemStack(Items.COBBLESTONE), level, cost)
            .isEmpty(), "mixed-material recipes are excluded");
        var furnace = level.getRecipeManager()
            .byKey(ResourceLocation.parse("minecraft:glass")).orElseThrow();
        require(
            RecipeOperations
                .smelting(furnace, new ItemStack(Items.SAND), level, cost)
                .orElseThrow().output().is(Items.GLASS),
            "ordinary smelting is discoverable");
        var calls = new AtomicInteger();
        java.util.function.Function<ItemStack, List<RecipeGraph.Operation>> resolver = source -> {
            calls.incrementAndGet();
            Item next = source.is(Items.COBBLESTONE)
                ? Items.GRAVEL
                : Items.COBBLESTONE;
            var recipe = recipe(source.getItem(), 1, next, 1, 5, 20);
            return List.of(RecipeOperations
                .machine(id(next == Items.GRAVEL ? "forward" : "back"),
                    id("grind"), recipe, source)
                .orElseThrow());
        };
        var cache = new RecipeGraph.Cache();
        var snapshot = new Object();
        var root = new ItemStack(Items.COBBLESTONE);
        var graph = cache.get(snapshot, root, resolver,
            RecipeGraph.Limits.defaults());
        require(
            graph.size() == 2 && graph.edges().size() == 2
                && graph.pathTo(1).size() == 1 && calls.get() == 2,
            "cycles are edges, never infinite expansion");
        cache.get(snapshot, root, resolver, RecipeGraph.Limits.defaults());
        require(calls.get() == 2, "same snapshot reuses discovery");
        cache.get(new Object(), root, resolver, RecipeGraph.Limits.defaults());
        require(calls.get() == 4, "reload invalidates discovery");
        cache.get(snapshot, root, resolver, RecipeGraph.Limits.defaults());
        require(
            cache.get(snapshot, root, resolver, new RecipeGraph.Limits(1, 2, 2))
                .truncated(),
            "changed limits invalidate the cached graph");
        require(RecipeGraph
            .discover(root, resolver, new RecipeGraph.Limits(1, 2, 2))
            .truncated(), "catalog limits report truncation");
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}
