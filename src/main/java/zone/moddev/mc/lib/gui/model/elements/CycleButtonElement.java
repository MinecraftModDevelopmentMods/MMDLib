package zone.moddev.mc.lib.gui.model.elements;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.ElementSnapshot;
import zone.moddev.mc.lib.gui.model.ProcessorVariable;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.TooltipContext;
import zone.moddev.mc.lib.gui.runtime.ScreenClick;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.machine.feature.Processor;

/** XML-defined cycle of processor modes, using declared server actions. */
public class CycleButtonElement implements ButtonControl {
    public record Mode(int value, ResourceLocation icon, Text label,
        Optional<Text> tooltip, ResourceLocation action) {
        public Mode {
            Objects.requireNonNull(icon);
            Objects.requireNonNull(label);
            Objects.requireNonNull(tooltip);
            Objects.requireNonNull(action);
        }
    }

    private final Common common;
    private final ProcessorVariable variable;
    private final List<Mode> modes;

    public CycleButtonElement(Common common, String variable,
        List<Mode> modes) {
        this.common = Objects.requireNonNull(common);
        this.variable = ProcessorVariable.parse(variable);
        this.modes = List.copyOf(modes);
    }

    public Common common() {
        return common;
    }
    public ProcessorVariable variable() {
        return variable;
    }
    public List<Mode> modes() {
        return modes;
    }
    public boolean flat() {
        return false;
    }
    public Optional<Text> text() {
        return Optional.empty();
    }
    public boolean interactive() {
        return true;
    }
    public int[] measure(TextMeasure measure) {
        return new int[]{18, 18};
    }

    public Optional<Mode> modeFor(Object value) {
        if (!(value instanceof Number number))
            return Optional.empty();
        int integer = number.intValue();
        if (number.doubleValue() != integer)
            return Optional.empty();
        return modes.stream().filter(mode -> mode.value() == integer)
            .findFirst();
    }

    /**
     * Unknown state recovers to the first declared mode; XML order owns
     * cycling.
     */
    public Mode nextMode(Object value) {
        int current = modeFor(value).map(modes::indexOf).orElse(-1);
        return modes.get((current + 1) % modes.size());
    }

    public Optional<ResourceLocation> iconFor(Object value) {
        return modeFor(value).or(() -> modes.stream().findFirst())
            .map(Mode::icon);
    }
    public Optional<Text> labelFor(Object value) {
        return modeFor(value).map(Mode::label);
    }

    public void appendTooltip(TooltipContext context, List<Component> rows) {
        modeFor(context.value()).ifPresent(mode -> {
            rows.add(context.text(mode.label()));
            mode.tooltip().ifPresent(text -> rows.add(context.text(text)));
        });
        context.appendDefaults(rows);
    }

    public void validate() {
        ButtonControl.super.validate();
        if (modes.size() < 2 || modes.size() > 256 || modes.stream()
            .map(Mode::value).distinct().count() != modes.size())
            throw new IllegalArgumentException(
                "cycle-button requires 2..256 modes with unique values");
        if (common.tooltip().isEmpty())
            throw new IllegalArgumentException(
                "cycle-button needs a tooltip saying how to cycle");
        if (common.value().isPresent() || common.max().isPresent()
            || common.clicks().any() || common.interaction().isPresent()
            || !common.jeiRecipes().isEmpty())
            throw new IllegalArgumentException(
                "cycle-button owns its variable and mode actions");
    }

    private Processor<?> processor(ScreenHost host) {
        if (host.screenFeatures() == null)
            throw new IllegalArgumentException(
                "cycle-button requires processor state");
        return host.screenFeatures().findProcessor(variable.processor())
            .orElseThrow(() -> new IllegalArgumentException(
                "Unknown processor: " + variable.processor()));
    }

    private void validateActions(Processor<?> processor) {
        for (var mode : modes)
            if (!processor.actions().containsKey(mode.action()))
                throw new IllegalArgumentException(
                    "Unknown cycle mode action: " + mode.action());
    }

    public void validateDefinition(Map<String, Processor<?>> processors) {
        if (modeFor(variable.defaultValue(processors)).isEmpty())
            throw new IllegalArgumentException(
                "cycle-button variable default must match a declared integer mode");
        validateActions(processors.get(variable.processor()));
    }
    public void validateHostType(Class<?> type) {
        if (!ScreenHost.class.isAssignableFrom(type))
            throw new IllegalArgumentException(
                "cycle-button requires a ScreenHost");
    }
    public void validateHost(ScreenHost host) {
        variable.read(host);
        validateActions(processor(host));
    }
    public Optional<ElementSnapshot> snapshot(ScreenHost host) {
        return Optional
            .of(new ElementSnapshot(variable.read(host), modes.size() - 1));
    }
    public boolean interact(ScreenClick click) {
        if (click.button() != 0 || click.option() != 0
            || !(click.menu().blockEntity instanceof ScreenHost host))
            return false;
        var next = nextMode(variable.read(host));
        var result = processor(host).invoke(next.action(), click.player());
        result.message().ifPresent(
            message -> click.player().displayClientMessage(message, true));
        return result.accepted();
    }

    @Override
    public boolean equals(Object other) {
        return other != null && other.getClass() == getClass()
            && common.equals(((CycleButtonElement) other).common)
            && variable.equals(((CycleButtonElement) other).variable)
            && modes.equals(((CycleButtonElement) other).modes);
    }
    @Override
    public int hashCode() {
        return Objects.hash(common, variable, modes);
    }
    @Override
    public String toString() {
        return "CycleButtonElement[common=" + common + ",variable=" + variable
            + ",modes=" + modes + "]";
    }
}
