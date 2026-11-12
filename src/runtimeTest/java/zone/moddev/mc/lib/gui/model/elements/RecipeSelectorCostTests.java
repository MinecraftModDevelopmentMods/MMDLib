package zone.moddev.mc.lib.gui.model.elements;

import java.util.List;
import java.util.Map;

import net.minecraft.core.HolderSet;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeBuilder;

@GameTestHolder("mmdlib")
@PrefixGameTestTemplate(false)
public final class RecipeSelectorCostTests {
    @GameTest(template = "empty")
    public static void reusableFluidCostsNeverReportConsumption(
        GameTestHelper h) {
        var fluids = HolderSet.direct(Fluids.LAVA.builtInRegistryHolder());
        var reusable = new MachineIngredient.FluidPart(fluids, 1000, false);
        var consumed = new MachineIngredient.FluidPart(fluids, 1000, true);
        require(
            RecipeSelectorElement
                .costs(new MachineRecipe(Map.of("lava", List.of(reusable)),
                    Map.of(), 1))
                .equals(List.of("lava: 1000 mB required, 0 mB consumed")),
            "Reusable fluid is required but not consumed");
        require(
            RecipeSelectorElement
                .costs(new MachineRecipe(Map.of("lava", List.of(consumed)),
                    Map.of(), 1))
                .equals(List.of("lava: 1000 mB required, 1000 mB consumed")),
            "Ordinary fluid retains consumption");
        var returned = MachineRecipeBuilder.recipe(null).input("lava", consumed)
            .outputFluid("lava", Fluids.LAVA, 250).time(1).build();
        require(
            RecipeSelectorElement.costs(returned)
                .equals(List.of("lava: 1000 mB required, 750 mB consumed")),
            "Deterministic return subtracts once");
        require(
            RecipeSelectorElement
                .costs(new MachineRecipe(
                    Map.of("lava", List.of(reusable, consumed)), Map.of(), 1))
                .contains("lava: 1000 mB required, 0 mB consumed"),
            "Mixed input groups retain reusable semantics");
        h.succeed();
    }
    private static void require(boolean value, String message) {
        if (!value)
            throw new AssertionError(message);
    }
}
