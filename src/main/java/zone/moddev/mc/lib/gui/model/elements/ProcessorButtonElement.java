package zone.moddev.mc.lib.gui.model.elements;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.ElementSnapshot;
import zone.moddev.mc.lib.gui.model.ProcessorButtonState;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.runtime.ScreenClick;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor;

/** Owns processor selection, state, pause action and redstone-conflict presentation. */
public class ProcessorButtonElement implements ButtonControl {
    private final Common common;
    private final Optional<String> processorKey;
    private final Optional<Text> text;
    private final boolean flat;

    public ProcessorButtonElement(
        Common common, Optional<String> processorKey, Optional<Text> text, boolean flat) {
        Objects.requireNonNull(common);
        Objects.requireNonNull(processorKey);
        Objects.requireNonNull(text);

        this.common = common;
        this.processorKey = processorKey;
        this.text = text;
        this.flat = flat;
    }

    public ProcessorButtonElement(Common common, Optional<String> key) {
        this(common, key, Optional.empty(), true);
    }

    public void validate() {
        ButtonControl.super.validate();
        if (processorKey.filter(String::isBlank).isPresent())
            throw new IllegalArgumentException("processor-key must not be blank");
        if (flat && text.isPresent())
            throw new IllegalArgumentException("A flat button uses an icon, not a text caption");
        if (common.value().isPresent()
            || common.max().isPresent()
            || common.clicks().any()
            || common.interaction().isPresent()
            || !common.jeiRecipes().isEmpty())
            throw new IllegalArgumentException(
                "<processor-button> owns its state and click action; value, max, click handlers,"
                    + " interaction and jei-recipes are not allowed");
    }

    public void validateHostType(Class<?> type) {
        if (!ScreenHost.class.isAssignableFrom(type))
            throw new IllegalArgumentException("<processor-button> requires a ScreenHost");
    }

    public void validateHost(ScreenHost host) {
        processor(host);
    }

    public void validateBindings(Map<String, Set<String>> bindings) {
        if (processorKey.isPresent()
            && !bindings.getOrDefault("processors", Set.of()).contains(processorKey.get()))
            throw new IllegalArgumentException("Unknown processor-key: " + processorKey.get());
    }

    private RecipeProcessor processor(ScreenHost host) {
        if (host.screenFeatures() == null)
            throw new IllegalArgumentException(
                "<processor-button> requires at least one recipe processor");
        return host.screenFeatures()
            .processor(processorKey)
            .orElseThrow(
                () -> new IllegalArgumentException(
                    processorKey
                        .map(key -> "Unknown processor-key: " + key)
                        .orElse("<processor-button> requires at least one recipe processor")));
    }

    public boolean interactive() {
        return true;
    }

    public boolean interact(ScreenClick click) {
        if (click.button() == 0
            && click.option() == 0
            && click.menu().blockEntity instanceof ScreenHost host) {
            var processor = processor(host);
            host.screenFeatures().setProcessorPaused(processor.key(), !processor.paused());
        }
        return true;
    }

    public Optional<ElementSnapshot> snapshot(ScreenHost host) {
        var processor = processor(host);
        var redstone = host.screenRedstone();
        int conflict = 0;
        if (redstone != null)
            for (int face = 0; face < 6; face++)
                if (redstone.mode(face) == RedstoneControl.Mode.READ
                    && redstone.paused() != processor.paused()) {
                    conflict = redstone.paused() ? 1 : 2;
                    break;
                }
        return Optional.of(
            new ElementSnapshot(ProcessorButtonState.of(processor).wireValue(), conflict));
    }

    public Optional<ResourceLocation> iconFor(Object value) {
        return Optional.of(
            ProcessorButtonState.fromWire(value).orElse(ProcessorButtonState.PAUSED).icon());
    }

    public Optional<Text> labelFor(Object value) {
        return Optional.of(
            new Text(
                ProcessorButtonState.fromWire(value).orElse(ProcessorButtonState.PAUSED).label(),
                Optional.empty()));
    }

    public Optional<Text> tooltipText() {
        return common
            .tooltip()
            .or(() -> Optional.of(new Text("screen.mmdlib.processor.toggle", Optional.empty())));
    }

    public Map<String, Text> tooltipArguments(Object value, Object maximum) {
        return ProcessorButtonState.fromWire(value)
            .map(state -> Map.of("action", new Text(state.action(), Optional.empty())))
            .orElse(Map.of());
    }

    public boolean conflict(Object maximum) {
        return maximum instanceof Number n && n.intValue() > 0;
    }

    public Optional<ResourceLocation> badge(Object value, Object maximum) {
        return conflict(maximum)
            ? Optional.of(ResourceLocation.parse("mmdlib:icon_processor_conflict"))
            : Optional.empty();
    }

    public List<Component> valueTooltip(Object value, Object maximum) {
        return conflict(maximum)
            ? List.of(
                Component.translatable(
                    ((Number) maximum).intValue() == 1
                        ? "screen.mmdlib.processor.conflict.redstone"
                        : "screen.mmdlib.processor.conflict.manual"))
            : List.of();
    }

    public Common common() {
        return common;
    }

    public Optional<String> processorKey() {
        return processorKey;
    }

    public Optional<Text> text() {
        return text;
    }

    public boolean flat() {
        return flat;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null || getClass() != object.getClass())
            return false;
        var other = (ProcessorButtonElement) object;
        return java.util.Objects.equals(common, other.common)
            && java.util.Objects.equals(processorKey, other.processorKey)
            && java.util.Objects.equals(text, other.text)
            && java.util.Objects.equals(flat, other.flat);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(common);
        hash = 31 * hash + java.util.Objects.hashCode(processorKey);
        hash = 31 * hash + java.util.Objects.hashCode(text);
        hash = 31 * hash + java.util.Objects.hashCode(flat);
        return hash;
    }

    @Override
    public String toString() {
        return "ProcessorButtonElement[common="
            + common
            + ", processorKey="
            + processorKey
            + ", text="
            + text
            + ", flat="
            + flat
            + "]";
    }
}
