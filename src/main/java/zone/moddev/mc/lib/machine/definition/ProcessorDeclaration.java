package zone.moddev.mc.lib.machine.definition;

import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;

/**
 * A validated mod-owned processor constructor; instantiated separately for each
 * machine.
 */
public record ProcessorDeclaration(Class<? extends StoredProcessor> type) {
    public static ProcessorDeclaration load(String name, ClassLoader loader) {
        try {
            var type = Class.forName(name, false, loader)
                .asSubclass(StoredProcessor.class);
            type.getConstructor();
            return new ProcessorDeclaration(type);
        } catch (ReflectiveOperationException | ClassCastException ex) {
            throw new IllegalArgumentException(
                "Invalid processor class: " + name, ex);
        }
    }

    public StoredProcessor create(String key, ProcessorMachine machine) {
        try {
            var processor = type.getConstructor().newInstance();
            processor.initialize(key, machine);
            return processor;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException(
                "Cannot instantiate processor: " + type.getName(), ex);
        }
    }
}
