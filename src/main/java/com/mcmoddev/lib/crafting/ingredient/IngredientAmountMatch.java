package com.mcmoddev.lib.crafting.ingredient;

import java.util.function.Function;
import net.minecraft.util.Tuple;

public enum IngredientAmountMatch {
    EXACT(tuple -> tuple.getFirst().intValue() == tuple.getSecond().intValue()),
    IGNORE_SIZE(tuple -> true),
    BE_ENOUGH(tuple -> tuple.getFirst() <= tuple.getSecond());

    private Function<Tuple<Integer, Integer>, Boolean> comparer;

    IngredientAmountMatch(Function<Tuple<Integer, Integer>, Boolean> comparer) {
        this.comparer = comparer;
    }

    public boolean compare(ICraftingIngredient a, ICraftingIngredient b) {
        return this.comparer.apply(new Tuple<>(a.getAmount(), b.getAmount()));
    }

    public boolean compare(int a, int b) {
        return this.comparer.apply(new Tuple<>(a, b));
    }
}
