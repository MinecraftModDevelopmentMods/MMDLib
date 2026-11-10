package zone.moddev.mc.lib.gui.load;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import zone.moddev.mc.lib.gui.model.Screen;

/**
 * Immutable definition-time inputs. Resource kinds and binding domains are open
 * string keys.
 */
public record ResourceValidationContext(Screen screen,
    Map<String, Integer> resources, Map<String, Integer> inventories,
    Map<String, Set<String>> bindings) {
    public ResourceValidationContext {
        Objects.requireNonNull(screen);
        resources = Map.copyOf(resources);
        inventories = Map.copyOf(inventories);
        bindings = bindings.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                entry -> Set.copyOf(entry.getValue())));
        if (resources.values().stream().anyMatch(count -> count < 0)
            || inventories.values().stream().anyMatch(count -> count < 0))
            throw new IllegalArgumentException("Negative resource count");
    }
}
