package zone.moddev.mc.lib.machine.definition;

import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;

/**
 * A validated mod-owned processor constructor; instantiated separately for each
 * machine.
 */
public final class ProcessorDeclaration {
    private final Class<? extends StoredProcessor> type;
    private final java.util.Map<String, zone.moddev.mc.lib.machine.feature.Processor<?>> schemas = new java.util.HashMap<>();

    public ProcessorDeclaration(Class<? extends StoredProcessor> type) {
        this.type = java.util.Objects.requireNonNull(type);
    }

    public Class<? extends StoredProcessor> type() {
        return type;
    }

    /** Cached read-only metadata; item hooks and live machines use create(). */
    public synchronized zone.moddev.mc.lib.machine.feature.Processor<?> schema(
        String key) {
        return schemas.computeIfAbsent(key, id -> new Schema(create(id, null)));
    }

    private static final class Schema implements
        zone.moddev.mc.lib.machine.feature.Processor<net.minecraft.nbt.CompoundTag> {
        private final StoredProcessor prototype;
        private final java.util.Map<String, Object> visuals;
        private final java.util.Map<net.minecraft.resources.ResourceLocation, zone.moddev.mc.lib.machine.feature.ProcessorAction> actions;

        Schema(StoredProcessor prototype) {
            this.prototype = prototype;
            var builder = new zone.moddev.mc.lib.machine.presentation.VisualVariables.Builder();
            prototype.defineVisualVariables(builder);
            visuals = builder.build().defaults();
            var declarations = new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation, zone.moddev.mc.lib.machine.feature.ProcessorAction>();
            prototype.actions().keySet().forEach(id -> declarations.put(id,
                new zone.moddev.mc.lib.machine.feature.ProcessorAction(
                    (state, context) -> false,
                    (state,
                        context) -> zone.moddev.mc.lib.machine.feature.ProcessorAction.Result
                            .unavailable())));
            actions = java.util.Map.copyOf(declarations);
        }
        public String key() {
            return prototype.key();
        }
        public com.mojang.serialization.Codec<net.minecraft.nbt.CompoundTag> codec() {
            return net.minecraft.nbt.CompoundTag.CODEC;
        }
        public net.minecraft.nbt.CompoundTag state() {
            return prototype.state();
        }
        public void restore(net.minecraft.nbt.CompoundTag data) {
            throw new UnsupportedOperationException(
                "Processor schema is read-only");
        }
        public boolean paused() {
            return false;
        }
        public void setPaused(boolean value) {
            throw new UnsupportedOperationException(
                "Processor schema is read-only");
        }
        public boolean processing() {
            return false;
        }
        public ProcessState executionStatus() {
            return ProcessState.IDLE;
        }
        public java.util.Map<String, Object> variableDefaults() {
            return prototype.variableDefaults();
        }
        public Object variable(String name) {
            return prototype.variable(name);
        }
        public java.util.Optional<zone.moddev.mc.lib.machine.feature.PortableVariable<?>> portableVariable(
            String name) {
            return prototype.portableVariable(name);
        }
        public java.util.Map<net.minecraft.resources.ResourceLocation, zone.moddev.mc.lib.machine.feature.ProcessorAction> actions() {
            return actions;
        }
        public void defineVisualVariables(
            zone.moddev.mc.lib.machine.presentation.VisualVariables.Builder builder) {
            visuals.forEach(
                (name, value) -> builder.add(name, value, () -> value));
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProcessorDeclaration declaration
            && type.equals(declaration.type);
    }
    @Override
    public int hashCode() {
        return type.hashCode();
    }
    @Override
    public String toString() {
        return "ProcessorDeclaration[type=" + type + "]";
    }
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
