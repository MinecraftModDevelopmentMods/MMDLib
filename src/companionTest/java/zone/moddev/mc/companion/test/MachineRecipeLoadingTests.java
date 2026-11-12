package zone.moddev.mc.companion.test;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;

import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.ScreenRuntime;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.neoforge.GeneratedMachineRecipes;
import zone.moddev.mc.lib.machine.neoforge.MachineRecipeTypes;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry;
import zone.moddev.mc.lib.machine.recipe.generation.ReloadTags;

@GameTestHolder("mmdlib_companion")
@PrefixGameTestTemplate(false)
public final class MachineRecipeLoadingTests {
    private static final ResourceLocation TYPE = ResourceLocation
        .parse("mmdlib_companion:simple_crushing");
    private static final ResourceLocation MACHINE = ResourceLocation
        .parse("mmdlib_companion:simple_crusher");
    private static final String SINGLE = """
        {"type":"mmdlib_companion:simple_crushing",
         "inputs":{"input":[{"items":{"item":"minecraft:cobblestone"}}]},
         "outputs":{"output":[{"item":{"id":"minecraft:gravel"}}]},"time":1}
        """;

    private static RecipeHolder<?> read(GameTestHelper h, String id,
        String json) {
        return new RecipeHolder<>(ResourceLocation.parse(id),
            Recipe.CODEC
                .parse(h.getLevel().registryAccess().createSerializationContext(
                    JsonOps.INSTANCE), JsonParser.parseString(json))
                .getOrThrow());
    }

    private static List<RecipeHolder<?>> load(GameTestHelper h,
        RecipeHolder<?>... recipes) {
        return GeneratedMachineRecipes.generate(List.of(recipes),
            h.getLevel().registryAccess(), new ReloadTags(Map.of()));
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }

    @GameTest(template = "empty")
    public static void authoredRecipesValidateGroupsAndResourceKinds(
        GameTestHelper h) {
        for (var json : List.of(SINGLE.replace("\"input\":", "\"typo\":"),
            SINGLE.replace("\"output\":", "\"typo\":"),
            SINGLE.replace("\"input\":", "\"energy\":"),
            SINGLE.replace("\"output\":", "\"energy\":"))) {
            try {
                load(h, read(h, "mmdlib_companion:bad_recipe", json));
                throw new AssertionError("Invalid authored recipe accepted");
            } catch (IllegalArgumentException expected) {
                require(
                    expected.getMessage()
                        .contains("mmdlib_companion:bad_recipe")
                        && expected.getMessage().contains(MACHINE.toString()),
                    "Error identifies both recipe and machine");
            }
        }
        require(load(h, read(h, "mmdlib_companion:valid_recipe", SINGLE))
            .size() == 1, "Single JSON recipes remain supported");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bundlesExpandWithStableIdsAndRejectCollisions(
        GameTestHelper h) {
        String prefix = "{\"type\":\"mmdlib:machine_recipes\",\"recipes\":{";
        var first = load(h, read(h, "mmdlib_companion:batch",
            prefix + "\"first\":" + SINGLE + ",\"second\":" + SINGLE + "}}"));
        var reordered = load(h, read(h, "mmdlib_companion:batch",
            prefix + "\"second\":" + SINGLE + ",\"first\":" + SINGLE + "}}"));
        require(
            first.stream().map(RecipeHolder::id).toList()
                .equals(reordered.stream().map(RecipeHolder::id).toList()),
            "Reordering does not change IDs");
        require(
            first.size() == 2 && first.stream()
                .allMatch(r -> r.value() instanceof MachineRecipeEntry),
            "Only flattened ordinary machine recipes survive");
        try {
            load(h,
                read(h, "mmdlib_companion:batch",
                    prefix + "\"first\":" + SINGLE + "}}"),
                read(h, "mmdlib_companion:batch/first", SINGLE));
            throw new AssertionError("Duplicate authored ID accepted");
        } catch (IllegalArgumentException expected) {
            require(
                expected.getMessage().contains("mmdlib_companion:batch/first"),
                "Duplicate diagnostic names stable ID");
        }
        var conditional = SINGLE.replace("\"time\":1",
            "\"neoforge:conditions\":[{\"type\":\"neoforge:false\"}],\"time\":1");
        require(
            load(h,
                read(h, "mmdlib_companion:conditional",
                    prefix + "\"skipped\":" + conditional + ",\"kept\":"
                        + SINGLE + "}}"))
                .size() == 1,
            "Per-entry conditions are respected");
        require(
            load(h, read(h, "mmdlib_companion:empty", prefix + "}}")).isEmpty(),
            "Empty replacement removes all bundle entries");
        try {
            load(h, read(h, "mmdlib_companion:bad_bundle", prefix + "\"bad\":"
                + SINGLE.replace("\"input\":", "\"typo\":") + "}}"));
            throw new AssertionError("Invalid bundled group accepted");
        } catch (IllegalArgumentException expected) {
            require(
                expected.getMessage()
                    .contains("mmdlib_companion:bad_bundle/bad"),
                "Bundled diagnostic names file and entry");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realBundleFileLoadsIntoTheProcessor(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        require(
            manager.byKey(ResourceLocation
                .parse("mmdlib_companion:simple_crushing/stone")).isEmpty(),
            "Bundle container removed before publication");
        for (var name : List.of("cobblestone", "gravel"))
            require(
                manager
                    .byKey(ResourceLocation.parse(
                        "mmdlib_companion:simple_crushing/stone/" + name))
                    .orElseThrow().value() instanceof MachineRecipeEntry,
                "Bundled recipe loaded at startup");
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(pos,
            BuiltInRegistries.BLOCK.get(MACHINE).defaultBlockState(), 3);
        var machine = (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
        machine.energy.setStored(2000);
        require(machine.items
            .insertItem(0, new ItemStack(Items.COBBLESTONE), false).isEmpty(),
            "Bundled input admitted");
        for (int i = 0; i < 101; i++)
            machine.serverTick();
        require(machine.items.getStackInSlot(1).is(Items.GRAVEL),
            "Bundled recipe processed normally");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void menuHandshakeIncludesMachineChecksum(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(pos,
            BuiltInRegistries.BLOCK.get(MACHINE).defaultBlockState(), 3);
        var machine = (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
        var screen = Screens.get(MACHINE).orElseThrow().screen();
        var player = h.makeMockServerPlayerInLevel();
        var expected = ScreenRuntime.fingerprint(screen, machine);
        require(!expected.equals(screen.fingerprint()),
            "Compatibility includes more than the screen");
        require(
            machine.screenDefinitionFingerprint()
                .equals(machine.definition.checksum()),
            "Host exposes resolved XML CRC");
        for (var fingerprint : List.of(expected, screen.fingerprint())) {
            var data = new RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(),
                h.getLevel().registryAccess());
            try {
                data.writeBlockPos(pos);
                data.writeUtf(fingerprint);
                data.writeLong(42);
                data.writeBlockPos(machine.getBlockPos());
                if (fingerprint.equals(expected)) {
                    require(
                        new MachineMenu(1, player.getInventory(), data)
                            .widgetSession() == 42,
                        "Matching definition opens normally");
                } else {
                    try {
                        new MachineMenu(1, player.getInventory(), data);
                        throw new AssertionError(
                            "Mismatched machine definition accepted");
                    } catch (IllegalStateException error) {
                        require(
                            error.getMessage()
                                .contains("machine definitions differ"),
                            "Clear compatibility diagnostic");
                    }
                }
            } finally {
                data.release();
            }
        }
        h.succeed();
    }
}
