package com.mcmoddev.lib.crafting.input;

import java.util.List;
import javax.annotation.Nullable;

public interface ITypedCraftingInput<I> extends ICraftingInput {
    Class<I> getInputClass();

    List<I> getPossibleInputs();

    @Nullable I getNullInput();
}
