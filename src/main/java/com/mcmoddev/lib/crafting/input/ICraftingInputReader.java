package com.mcmoddev.lib.crafting.input;

import javax.annotation.Nullable;
import com.google.gson.JsonElement;

/**
 * Interface used to read ingredients from a json file.
 */
public interface ICraftingInputReader {
    /**
     * @return The key used to store this reader in the registry.
     */
    String getKey();

    /**
     * Parses the JsonElement into a ICraftingInput.
     * @param index Input position used to generate keys in case not specified in other ways.
     * @param json The json storing the crafting input.
     * @return The parsed ICraftingInput, or null in case the json element wasn't recognized by this reader.
     */
    @Nullable
    ICraftingInput parse(int index, JsonElement json);
}
