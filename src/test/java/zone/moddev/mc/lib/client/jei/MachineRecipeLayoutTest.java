package zone.moddev.mc.lib.client.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MachineRecipeLayoutTest {
    @Test
    void ghostInputsAreSeparatedFromConsumedIngredients() {
        var stone = new MachineRecipeLayout(2, 1, 1);
        assertTrue(stone.separator());
        assertEquals(48, stone.inputX());
        assertEquals(70, stone.arrowX());
        assertEquals(98, stone.outputX());
        assertEquals(116, stone.width());
        assertEquals(1, stone.rows());
    }

    @Test
    void categoriesWithoutConsumedInputsDoNotLeaveAnEmptyIngredientSection() {
        var cobblestone = new MachineRecipeLayout(3, 0, 1);
        assertFalse(cobblestone.separator());
        assertEquals(58, cobblestone.arrowX());
        assertEquals(104, cobblestone.width());
        var ordinary = new MachineRecipeLayout(0, 2, 1);
        assertFalse(ordinary.separator());
        assertEquals(0, ordinary.inputX());
        assertEquals(86, ordinary.width());
    }

    @Test
    void categoryReservesSharedColumnsEvenWhenARecipeHasNoConsumedInputs() {
        var cobblestone = new MachineRecipeLayout(3, 0, 1);
        var stone = new MachineRecipeLayout(3, 1, 1);
        var category = cobblestone.merge(stone);
        assertEquals(stone, category);
        assertEquals(category, stone.merge(cobblestone));
        assertEquals(66, category.inputX());
        assertEquals(88, category.arrowX());
        assertEquals(116, category.outputX());
        assertFalse(cobblestone.separator());
        assertTrue(stone.separator());

        // Different recipes may supply the widest section on each side of the arrow.
        var mixed = new MachineRecipeLayout(3, 0, 1)
            .merge(new MachineRecipeLayout(0, 3, 5));
        assertEquals(new MachineRecipeLayout(3, 3, 5), mixed);
        assertEquals(206, mixed.width());
        assertEquals(2, mixed.rows());
    }

    @Test
    void eachSectionWrapsAfterThreeSlots() {
        var multiple = new MachineRecipeLayout(4, 7, 5);
        assertEquals(3, multiple.rows());
        assertEquals(66, multiple.inputX());
        assertEquals(152, multiple.outputX());
        assertEquals(206, multiple.width());
        assertEquals(1, new MachineRecipeLayout(0, 0, 0).rows());
    }

    @Test
    void fluidScalesUseWholeBucketsAndDoNotOverflow() {
        for (int amount : new int[]{1, 100, 250, 999, 1000})
            assertEquals(1000, MachineRecipeLayout.fluidCapacity(amount));
        for (int amount : new int[]{1001, 1250, 2000})
            assertEquals(2000, MachineRecipeLayout.fluidCapacity(amount));
        assertEquals(3000, MachineRecipeLayout.fluidCapacity(2001));
        assertEquals(2147484000L, MachineRecipeLayout.fluidCapacity(Integer.MAX_VALUE));
    }
}
