package zone.moddev.mc.companion.test;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.neoforge.GeneratedMachineRecipes;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;

@GameTestHolder("mmdlib_companion")
@PrefixGameTestTemplate(false)
public final class MachineDefinitionValidationTests {
    private static MachineDefinition definition(String storage, int samples,
        String item) {
        var d = MachineContent.definitions()
            .get(ResourceLocation.parse("mmdlib_companion:simple_crusher"));
        return new MachineDefinition(d.id(), d.checksum(), d.name(),
            d.hardness(), d.mineable(), d.requiresTool(), d.textures(),
            d.baseModel(), d.modelParts(), d.faceTextures(), d.power(),
            d.fluids(), storage, d.slots(), d.groups(), d.recipeType(),
            d.processor(), d.processorName(), d.redstone(), d.screen(),
            d.recipeSelection(), List.of(new MachineDefinition.Ghost("pattern",
                samples, item == null ? null : ResourceLocation.parse(item))));
    }

    @GameTest(template = "empty")
    public static void ghostDefaultsValidateBeforeWorldConstruction(
        GameTestHelper h) {
        validateResources(definition("items", 1, "minecraft:stone"));
        validateResources(definition("items", 1, null));
        for (var item : List.of("mmdlib_companion:typo", "minecraft:air")) {
            try {
                validateResources(definition("items", 1, item));
                throw new AssertionError(
                    "Invalid ghost default accepted: " + item);
            } catch (IllegalArgumentException expected) {
                require(
                    expected.getMessage().contains("simple_crusher")
                        && expected.getMessage().contains("pattern")
                        && expected.getMessage().contains(item),
                    "Startup diagnostic identifies the machine, ghost group and invalid item");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ghostRecipesCannotRequireMoreSamplesThanSlots(
        GameTestHelper h) {
        var one = new MachineIngredient.ItemPart(Ingredient.of(Items.STONE), 1);
        var two = new MachineIngredient.ItemPart(Ingredient.of(Items.STONE), 2);
        validateGroups(definition("items", 1, null),
            new MachineRecipe(Map.of("pattern", List.of(one)), Map.of(), 1));
        // Multiple samples can jointly satisfy a count greater than one.
        validateGroups(definition("items", 2, null),
            new MachineRecipe(Map.of("pattern", List.of(two)), Map.of(), 1));
        for (List<MachineIngredient> inputs : List.of(
            List.<MachineIngredient>of(two),
            List.<MachineIngredient>of(one, one))) {
            try {
                validateGroups(definition("items", 1, null),
                    new MachineRecipe(Map.of("pattern", inputs), Map.of(), 1));
                throw new AssertionError("Impossible ghost recipe accepted");
            } catch (IllegalArgumentException expected) {
                require(
                    expected.getMessage().contains("pattern")
                        && expected.getMessage().contains("samples"),
                    "Diagnostic identifies the impossible sample requirement");
            }
        }
        h.succeed();
    }

    private static DefinedMachineEntity entity(MachineDefinition d) {
        return new DefinedMachineEntity(
            BuiltInRegistries.BLOCK_ENTITY_TYPE.get(d.id()), BlockPos.ZERO,
            BuiltInRegistries.BLOCK.get(d.id()).defaultBlockState(), d,
            MachineContent.recipeType(d.recipeType().id()));
    }

    @GameTest(template = "empty")
    public static void oldStorageNamesAndGhostsRoundTripIndependently(
        GameTestHelper h) {
        for (var key : List.of("multiblock", "multiblock_view",
            "mmdlib_ghost_items")) {
            var d = definition(key, 1, "minecraft:stone");
            var original = entity(d);
            original.items.insertResult(0, new ItemStack(Items.DIAMOND, 3),
                false);
            original.setScreenGhost(null, "pattern", 0,
                new ItemStack(Items.GOLD_INGOT));
            var saved = original
                .saveWithFullMetadata(h.getLevel().registryAccess());
            require(saved.contains(key),
                "Consumer storage keeps its original save key");
            require(
                saved.getCompound("mmdlib_machine")
                    .contains("mmdlib_ghost_items"),
                "Ghost metadata uses the existing reserved namespace");
            var restored = entity(d);
            restored.loadWithComponents(saved, h.getLevel().registryAccess());
            require(
                restored.items.getStackInSlot(0).getCount() == 3
                    && restored.items.getStackInSlot(0).is(Items.DIAMOND),
                "Real items survive reload");
            require(restored.screenGhost("pattern", 0).is(Items.GOLD_INGOT),
                "Ghost sample survives reload");
        }
        var d = definition("items", 1, "minecraft:stone");
        var original = entity(d);
        original.setScreenGhost(null, "pattern", 0,
            new ItemStack(Items.GOLD_INGOT));
        var saved = original
            .saveWithFullMetadata(h.getLevel().registryAccess());
        saved.put("mmdlib_ghost_items", saved.getCompound("mmdlib_machine")
            .getCompound("mmdlib_ghost_items"));
        saved.remove("mmdlib_machine");
        var restored = entity(d);
        restored.loadWithComponents(saved, h.getLevel().registryAccess());
        require(restored.screenGhost("pattern", 0).is(Items.GOLD_INGOT),
            "Pre-release flat ghost saves migrate");
        h.succeed();
    }

    private static void validateResources(MachineDefinition definition) {
        validate(MachineContent.class, "validateResources",
            new Class<?>[]{MachineDefinition.class}, definition);
    }

    private static void validateGroups(MachineDefinition definition,
        MachineRecipe recipe) {
        validate(GeneratedMachineRecipes.class, "validateGroups",
            new Class<?>[]{MachineDefinition.class, MachineRecipe.class},
            definition, recipe);
    }

    // Keep production validators internal; the companion lives in a separate
    // mod/module.
    private static void validate(Class<?> owner, String name, Class<?>[] types,
        Object... args) {
        try {
            var method = owner.getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(null, args);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException cause)
                throw cause;
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void require(boolean value, String message) {
        if (!value)
            throw new AssertionError(message);
    }
}
