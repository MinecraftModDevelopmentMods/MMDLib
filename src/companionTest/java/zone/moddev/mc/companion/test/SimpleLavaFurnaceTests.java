package zone.moddev.mc.companion.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;
import zone.moddev.mc.lib.machine.neoforge.MachineRecoveryItem;
import zone.moddev.mc.lib.machine.recipe.MachineProduct;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;
import zone.moddev.mc.lib.machine.recipe.generation.GeneratorProperties;
import zone.moddev.mc.lib.machine.recipe.generation.MachineRecipeOutput;
import zone.moddev.mc.lib.machine.recipe.generation.OreToIngotGenerator;
import zone.moddev.mc.lib.machine.recipe.generation.RecipeGenerationContext;
import zone.moddev.mc.lib.machine.recipe.generation.RecipeTransforms;
import zone.moddev.mc.lib.machine.recipe.generation.ReloadTags;

@GameTestHolder("mmdlib_companion")
@PrefixGameTestTemplate(false)
public final class SimpleLavaFurnaceTests {
    private static final ResourceLocation ID = ResourceLocation
        .parse("mmdlib_companion:simple_lava_furnace");
    private static final ResourceLocation TYPE = ResourceLocation
        .parse("mmdlib_companion:lava_smelting");

    private static DefinedMachineEntity place(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(pos,
            BuiltInRegistries.BLOCK.get(ID).defaultBlockState(), 3);
        return (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
    }

    private static void require(boolean value, String message) {
        if (!value)
            throw new AssertionError(message);
    }

    @GameTest(template = "empty")
    public static void tankTooltipDescribesContentsAndEachViewersContainer(
        GameTestHelper h) {
        var machine = place(h);
        var gauge = Screens.get(ID).orElseThrow().screen().find("tank_0")
            .orElseThrow();
        var displayFluid = new FluidStack(Fluids.LAVA, 250);
        require(gauge.displayValue(displayFluid).equals(250),
            "Tank owns fluid display conversion");
        require(gauge.fillRatio(displayFluid, 1000) == .25,
            "Tank owns fluid fill ratio");
        require(
            gauge.displayText(displayFluid, false).getString().equals("250"),
            "Fluid display amount");
        require(displayFluid.getAmount() == 250,
            "Presentation does not mutate synchronized fluid");
        var empty = gauge.tooltip(machine, ItemStack.EMPTY);
        require(
            empty.size() == 3 && empty.getFirst().getString().equals("fuel"),
            "Group name first");
        require(
            ((net.minecraft.network.chat.contents.TranslatableContents) empty
                .get(1).getContents()).getKey()
                .equals("screen.mmdlib.tank.empty"),
            "Empty tank described");
        machine.fluids.fill(new FluidStack(Fluids.LAVA, 100),
            FluidAction.EXECUTE);
        var lava = new ItemStack(Items.LAVA_BUCKET);
        var water = new ItemStack(Items.WATER_BUCKET);
        var allowed = gauge.tooltip(machine, lava);
        var rejected = gauge.tooltip(machine, water);
        var contents = (net.minecraft.network.chat.contents.TranslatableContents) allowed
            .get(1).getContents();
        require(contents.getKey().equals("screen.mmdlib.tank.contents")
            && contents.getArgs()[0].equals(100), "Actual fluid amount");
        require(
            allowed.getLast().getStyle().getColor()
                .getValue() == net.minecraft.ChatFormatting.BLUE.getColor(),
            "Accepted container is blue");
        require(
            rejected.getLast().getStyle().getColor()
                .getValue() == net.minecraft.ChatFormatting.RED.getColor(),
            "Rejected container is red");
        require(
            lava.is(Items.LAVA_BUCKET) && water.is(Items.WATER_BUCKET)
                && machine.fluids.getFluidInTank(0).getAmount() == 100,
            "Tooltip never transfers contents");
        require(gauge.tooltip(machine, new ItemStack(Items.BUCKET)).size() == 3,
            "Empty containers add no misleading fluid line");
        var portable = new ItemStack(Items.GLASS_BOTTLE);
        portable.set(DataComponents.CUSTOM_NAME,
            net.minecraft.network.chat.Component.literal("Tank"));
        net.neoforged.neoforge.fluids.FluidUtil.getFluidHandler(portable)
            .orElseThrow()
            .fill(new FluidStack(Fluids.WATER, 350), FluidAction.EXECUTE);
        var tankLine = gauge.tooltip(machine, portable).getLast();
        var tankText = (net.minecraft.network.chat.contents.TranslatableContents) tankLine
            .getContents();
        require(tankText.getKey().equals("screen.mmdlib.tank.container_rejects")
            && ((net.minecraft.network.chat.Component) tankText.getArgs()[1])
                .getString().equals("Tank"),
            "Modded containers use their own display name");
        require(
            net.neoforged.neoforge.fluids.FluidUtil.getFluidHandler(portable)
                .orElseThrow().getFluidInTank(0).getAmount() == 350,
            "Portable tank remains unchanged");
        var player = h.makeMockServerPlayerInLevel();
        var first = new zone.moddev.mc.lib.gui.runtime.MachineMenu(81,
            player.getInventory(), machine);
        var second = new zone.moddev.mc.lib.gui.runtime.MachineMenu(82,
            player.getInventory(), machine);
        first.setCarried(lava);
        second.setCarried(water);
        first.components().snapshot();
        second.components().snapshot();
        var firstTips = first.components().requireCapability(
            zone.moddev.mc.lib.gui.runtime.component.ServerTooltips.class);
        var secondTips = second.components().requireCapability(
            zone.moddev.mc.lib.gui.runtime.component.ServerTooltips.class);
        require(
            firstTips.lines(gauge).equals(allowed)
                && secondTips.lines(gauge).equals(rejected),
            "Viewer-specific cursor is not shared between menus");
        ((net.minecraft.network.chat.MutableComponent) firstTips.lines(gauge)
            .getFirst()).withStyle(net.minecraft.ChatFormatting.OBFUSCATED);
        require(firstTips.lines(gauge).equals(allowed),
            "Reading tooltip rows cannot mutate synchronized state");
        first.setCarried(ItemStack.EMPTY);
        first.components().snapshot();
        require(
            firstTips.lines(gauge).size() == 3
                && secondTips.lines(gauge).equals(rejected),
            "Removing cursor clears acceptance line");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void generatedGaugeFillsAndManuallyDrainsOnlyUnreservedFluid(
        GameTestHelper h) {
        var machine = place(h);
        var player = h.makeMockServerPlayerInLevel();
        var pos = machine.getBlockPos();
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        player.getAbilities().instabuild = false;
        var menu = new zone.moddev.mc.lib.gui.runtime.MachineMenu(47,
            player.getInventory(), machine) {
            @Override
            public void broadcastChanges() {
            }
        };
        player.containerMenu = menu;
        var gauge = menu.bound.screen().find("tank_0").orElseThrow();
        int index = menu.index(gauge);
        var left = new zone.moddev.mc.lib.gui.runtime.ScreenNetwork.Action(47,
            index, 0, 0, 0, 0);
        var right = new zone.moddev.mc.lib.gui.runtime.ScreenNetwork.Action(47,
            index, 0, 1, 0, 0);
        menu.setCarried(new ItemStack(Items.WATER_BUCKET));
        menu.action(player, left);
        require(
            machine.fluids.getFluidInTank(0).isEmpty()
                && menu.getCarried().is(Items.WATER_BUCKET),
            "Recipe filter rejects wrong fluid");
        menu.setCarried(new ItemStack(Items.LAVA_BUCKET));
        menu.action(player, right);
        require(machine.fluids.getFluidInTank(0).isEmpty(),
            "Right click never pours into tank");
        menu.action(player, left);
        require(
            machine.fluids.getFluidInTank(0).getAmount() == 1000
                && menu.getCarried().is(Items.BUCKET),
            "Generated gauge pours into bound tank");
        menu.action(player, left);
        require(machine.fluids.getFluidInTank(0).getAmount() == 1000,
            "Left click never drains tank");
        machine.fluids.setPlan(0,
            new zone.moddev.mc.lib.machine.inventory.TankPlan(100,
                zone.moddev.mc.lib.machine.ingredient.FluidAmount.EMPTY));
        menu.action(player, right);
        require(
            machine.fluids.getFluidInTank(0).getAmount() == 1000
                && menu.getCarried().is(Items.BUCKET),
            "Bucket cannot take reserved fluid");
        machine.fluids.setPlan(0,
            zone.moddev.mc.lib.machine.inventory.TankPlan.NONE);
        menu.action(player, right);
        require(
            machine.fluids.getFluidInTank(0).isEmpty()
                && menu.getCarried().is(Items.LAVA_BUCKET),
            "Input tank allows manual bucket extraction");
        player.getAbilities().instabuild = true;
        menu.action(player, left);
        require(
            machine.fluids.getFluidInTank(0).getAmount() == 1000
                && menu.getCarried().is(Items.LAVA_BUCKET),
            "Creative transfer keeps container");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void smeltsWithLavaAndResumesReservedCycle(GameTestHelper h) {
        var machine = place(h);
        require(machine.getClass() == DefinedMachineEntity.class
            && machine.energy == null, "Shared runtime without FE storage");
        require(
            h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                machine.getBlockPos(), Direction.NORTH) == null,
            "No FE capability");
        var fluid = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
            machine.getBlockPos(), Direction.NORTH);
        require(fluid != null && fluid.getTankCapacity(0) == 8000,
            "Declared fluid capability");
        require(fluid.fill(new FluidStack(Fluids.WATER, 1000),
            FluidAction.EXECUTE) == 0, "Rejects water");
        require(fluid.fill(new FluidStack(Fluids.LAVA, 1000),
            FluidAction.EXECUTE) == 1000, "Accepts lava");
        require(fluid.drain(1000, FluidAction.EXECUTE).isEmpty(),
            "Input tank refuses external extraction");
        machine.items.insertItem(0, new ItemStack(Items.RAW_IRON), false);
        for (int i = 0; i < 26; i++)
            machine.serverTick();
        require(machine.getProgress() == 25 && machine.getTime() == 200,
            "Preserves smelting time");
        var saved = machine.saveWithoutMetadata(h.getLevel().registryAccess());
        machine.loadWithComponents(saved, h.getLevel().registryAccess());
        for (int i = 0; i < 175; i++)
            machine.serverTick();
        require(machine.items.getStackInSlot(0).isEmpty(),
            "Consumed input once");
        require(
            machine.items.getStackInSlot(1).is(Items.IRON_INGOT)
                && machine.items.getStackInSlot(1).getCount() == 1,
            "Preserves original smelting yield");
        require(machine.fluids.getFluidInTank(0).getAmount() == 900,
            "Consumes 100 mB once across save/load");
        var screen = Screens.get(ID).orElseThrow().screen();
        require(
            screen.find("tank_0").isPresent()
                && screen.find("energy").isEmpty(),
            "Generated tank gauge without FE gauge");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void noFuelBlockedOutputAndPauseKeepInputs(GameTestHelper h) {
        var machine = place(h);
        machine.items.insertItem(0, new ItemStack(Items.RAW_IRON), false);
        for (int i = 0; i < 10; i++)
            machine.serverTick();
        require(
            machine.getProgress() == 0
                && machine.items.getStackInSlot(0).getCount() == 1,
            "No fuel leaves input intact");
        machine.fluids.fill(new FluidStack(Fluids.LAVA, 1000),
            FluidAction.EXECUTE);
        machine.items.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 64));
        for (int i = 0; i < 10; i++)
            machine.serverTick();
        require(
            machine.getProgress() == 0
                && machine.fluids.getFluidInTank(0).getAmount() == 1000,
            "Blocked output consumes no lava");
        machine.items.setStackInSlot(1, ItemStack.EMPTY);
        for (int i = 0; i < 21; i++)
            machine.serverTick();
        var processor = machine.features.processor(Optional.empty())
            .orElseThrow();
        processor.pause();
        for (int i = 0; i < 10; i++)
            machine.serverTick();
        require(
            machine.getProgress() == 20
                && machine.fluids.getFluidInTank(0).getAmount() == 1000,
            "Pause releases reservation without consumption");
        processor.resume();
        for (int i = 0; i < 181; i++)
            machine.serverTick();
        require(
            machine.items.getStackInSlot(1).is(Items.IRON_INGOT)
                && machine.fluids.getFluidInTank(0).getAmount() == 900,
            "Resume produces once");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void recoveryRestoresFluidAndRetainsEnergyWithoutBuffer(
        GameTestHelper h) {
        var machine = place(h);
        machine.fluids.fill(new FluidStack(Fluids.LAVA, 7950),
            FluidAction.EXECUTE);
        var data = new CompoundTag();
        data.put("fluid", new FluidStack(Fluids.LAVA, 200)
            .save(h.getLevel().registryAccess()));
        var entry = new CompoundTag();
        entry.putString("type", ResourceType.FLUIDS.id().toString());
        entry.put("data", data);
        var entries = new ListTag();
        entries.add(entry);
        var payload = new CompoundTag();
        payload.put("resources", entries);
        payload.putLong("refund", 100);
        var capsule = MachineRecoveryItem.create(payload);
        require(MachineRecoveryItem.restore(capsule, machine),
            "Restores available capacity");
        require(machine.fluids.getFluidInTank(0).getAmount() == 8000,
            "Filled available space");
        var remainder = capsule.get(DataComponents.CUSTOM_DATA).copyTag()
            .getCompound("mmdlib_recovery");
        require(remainder.getLong("refund") == 100,
            "No power storage preserves energy refund");
        require(FluidStack
            .parseOptional(h.getLevel().registryAccess(),
                remainder.getList("resources", Tag.TAG_COMPOUND).getCompound(0)
                    .getCompound("data").getCompound("fluid"))
            .getAmount() == 150, "Fluid overflow retained");
        require(!MachineRecoveryItem.restore(capsule, machine),
            "Repeated restore cannot duplicate fluids");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bucketsFillAndInvalidSavedFluidIsPreservedForRecovery(
        GameTestHelper h) {
        var machine = place(h);
        var player = h
            .makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var hit = new net.minecraft.world.phys.BlockHitResult(
            net.minecraft.world.phys.Vec3.atCenterOf(machine.getBlockPos()),
            Direction.UP, machine.getBlockPos(), false);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
            new ItemStack(Items.LAVA_BUCKET));
        var result = machine.getBlockState().useItemOn(player.getMainHandItem(),
            h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND,
            hit);
        require(
            result == net.minecraft.world.ItemInteractionResult.CONSUME
                && player.getMainHandItem().is(Items.BUCKET)
                && machine.fluids.getFluidInTank(0).getAmount() == 1000,
            "Bucket fills tank and returns empty bucket");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
            new ItemStack(Items.WATER_BUCKET));
        require(machine.getBlockState().useItemOn(player.getMainHandItem(),
            h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND,
            hit) == net.minecraft.world.ItemInteractionResult.CONSUME,
            "Wrong fluid blocks vanilla bucket placement");
        require(
            player.getMainHandItem().is(Items.WATER_BUCKET)
                && machine.fluids.getFluidInTank(0).getAmount() == 1000,
            "Rejected bucket preserves contents");
        machine.fluids.restoreFluid(0, new FluidStack(Fluids.WATER, 500));
        var saved = machine.saveWithoutMetadata(h.getLevel().registryAccess());
        machine.loadWithComponents(saved, h.getLevel().registryAccess());
        require(
            machine.fluids.getFluidInTank(0).isEmpty()
                && !machine.fluids.recoveryContents().isEmpty(),
            "Fluid rejected by changed tank filter is recoverable");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oreSubclassTransformsBeforeEmission(GameTestHelper h) {
        var ore = BuiltInRegistries.ITEM.wrapAsHolder(Items.IRON_ORE);
        var ingot = BuiltInRegistries.ITEM.wrapAsHolder(Items.IRON_INGOT);
        var tags = new ReloadTags(
            Map.of(ResourceLocation.parse("c:ores/iron"), List.of(ore),
                ResourceLocation.parse("c:ingots/iron"), List.of(ingot)));
        var context = new RecipeGenerationContext(
            MachineContent.recipeType(TYPE), h.getLevel().registryAccess(),
            tags, List.of(), new GeneratorProperties(
                Map.of("energy-per-mb", "40"), "test.xml", 1));
        var transformed = new ArrayList<MachineRecipe>();
        new LavaOres().generate(context,
            (id, recipe) -> transformed.add(recipe));
        var result = transformed.getFirst();
        require(result.time() == 100 && !result.inputs().containsKey("energy"),
            "Subclass removes FE and preserves ore time");
        require(((MachineIngredient.FluidPart) result.inputs().get("fuel")
            .getFirst()).amount() == 50, "Ore energy converted to lava");
        require(
            ((MachineProduct.ItemProduct) result.outputs().get("output")
                .getFirst()).stack().getCount() == 2,
            "Ore generator still doubles output");
        var mixed = new MachineRecipe(
            Map.of("input",
                List.of(new MachineIngredient.EnergyPart(41),
                    new MachineIngredient.ItemPart(
                        net.minecraft.world.item.crafting.Ingredient
                            .of(Items.RAW_IRON),
                        1)),
                "other", List.of(new MachineIngredient.EnergyPart(40))),
            result.outputs(), 17);
        var converted = RecipeTransforms.energyToFluid(mixed, "fuel",
            HolderSet.direct(Fluids.LAVA.builtInRegistryHolder()), 40);
        require(
            ((MachineIngredient.FluidPart) converted.inputs().get("fuel")
                .getFirst()).amount() == 3
                && !converted.inputs().containsKey("other"),
            "All energy groups summed and rounded up");
        require(
            converted.inputs().get("input").size() == 1
                && converted.time() == 17
                && converted.outputs().equals(mixed.outputs()),
            "Retains nonenergy inputs, outputs and duration");
        h.succeed();
    }

    public static final class LavaOres extends OreToIngotGenerator {
        @Override
        public void generate(RecipeGenerationContext ctx,
            MachineRecipeOutput output) {
            long rate = ctx.properties().longValue("energy-per-mb", 40, 1,
                Long.MAX_VALUE);
            super.generate(
                ctx.withProperties(ctx.properties().without("energy-per-mb")),
                (id, recipe) -> output
                    .accept(id,
                        RecipeTransforms.energyToFluid(recipe, "fuel",
                            HolderSet.direct(ctx.registries().lookupOrThrow(
                                net.minecraft.core.registries.Registries.FLUID)
                                .getOrThrow(
                                    Fluids.LAVA.builtInRegistryHolder().key())),
                            rate)));
        }
    }
}
