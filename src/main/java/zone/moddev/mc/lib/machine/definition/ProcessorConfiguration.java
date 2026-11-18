package zone.moddev.mc.lib.machine.definition;

import java.util.List;
import java.util.Map;

/**
 * A stable processor identity and its private view of shared physical
 * resources.
 */
public record ProcessorConfiguration(String id, String name,
    MachineDefinition.RecipeType recipeType, ProcessorDeclaration declaration,
    Map<String, String> bindings, List<String> filterInputs,
    MachineDefinition.RecipeSelection selection,
    MachineDefinition.Sounds sounds, MachineDefinition.Events events,
    long energyPerTick) {
    public ProcessorConfiguration(String id, String name,
        MachineDefinition.RecipeType recipeType,
        ProcessorDeclaration declaration, Map<String, String> bindings,
        List<String> filterInputs, MachineDefinition.RecipeSelection selection,
        MachineDefinition.Sounds sounds, MachineDefinition.Events events) {
        this(id, name, recipeType, declaration, bindings, filterInputs,
            selection, sounds, events, 0);
    }
    public ProcessorConfiguration {
        if (id == null || id.isBlank()
            || (recipeType == null) == (declaration == null))
            throw new IllegalArgumentException(
                "A processor needs an ID and exactly one implementation");
        if (energyPerTick < 0)
            throw new IllegalArgumentException(
                "Negative processor energy cost");
        bindings = java.util.Collections
            .unmodifiableMap(new java.util.LinkedHashMap<>(bindings));
        filterInputs = List.copyOf(filterInputs);
    }
}
