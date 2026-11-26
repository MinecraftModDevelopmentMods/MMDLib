package zone.moddev.mc.lib.gui.model.elements;

import static zone.moddev.mc.lib.gui.model.ValueContract.NUMBERS;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.ProgressDirection;
import zone.moddev.mc.lib.gui.model.ValueContract;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.sync.ElementStateView;
import zone.moddev.mc.lib.gui.sync.ElementSync;
import zone.moddev.mc.lib.gui.sync.SynchronizedElement;
import zone.moddev.mc.lib.gui.value.ValueType;

/**
 * {@code progress}: a fill-in progress icon.
 *
 * <p>
 * {@code icon}: the icon sprite; the default arrow when empty
 * {@code direction}: which way it fills
 */
public class ProgressElement implements SynchronizedElement {
    public static final ElementSync<Boolean> OUTPUT_BLOCKED = ElementSync
        .field(ResourceLocation.parse("mmdlib:progress_output_blocked"),
            ByteBufCodecs.BOOL.cast(), 1, element -> false)
        .sample(source -> ((ProgressElement) source.element())
            .outputBlocked(source.machine()))
        .immutable().build();

    private final Common common;
    private final Optional<ResourceLocation> icon;
    private final ProgressDirection direction;
    private final Optional<String> processorKey;

    public ProgressElement(Common common, Optional<ResourceLocation> icon,
        ProgressDirection direction) {
        this(common, icon, direction, Optional.empty());
    }

    public ProgressElement(Common common, Optional<ResourceLocation> icon,
        ProgressDirection direction, Optional<String> processorKey) {
        this.common = common;
        this.icon = icon;
        this.direction = direction;
        this.processorKey = processorKey;
    }

    private boolean outputBlocked(BlockEntity machine) {
        return machine instanceof ScreenHost host
            && host.screenFeatures() != null
            && processorKey.flatMap(host.screenFeatures()::processor)
                .map(processor -> processor.outputBlocked(machine.getLevel()))
                .orElse(false);
    }

    public boolean outputBlocked(ElementStateView state) {
        return processorKey.isPresent() && state.get(this, OUTPUT_BLOCKED);
    }

    public List<ElementSync<?>> synchronizedValues() {
        return processorKey.isPresent() ? List.of(OUTPUT_BLOCKED) : List.of();
    }

    public void validateBindings(Map<String, Set<String>> bindings) {
        processorKey.ifPresent(key -> {
            if (!bindings.getOrDefault("processors", Set.of()).contains(key))
                throw new IllegalArgumentException(
                    "Unknown processor-key: " + key);
        });
    }

    /** The processor arrow is a preset of the reusable progress sprite. */
    public ProgressSpriteElement spriteElement() {
        boolean vertical = direction == ProgressDirection.UP
            || direction == ProgressDirection.DOWN;
        return new ProgressSpriteElement(common,
            icon.orElse(ResourceLocation.parse("mmdlib:progress_arrow")),
            icon.orElse(ResourceLocation.parse("mmdlib:progress_arrow_filled")),
            vertical ? 16 : 24, vertical ? 24 : 16, direction, 0xffffffff,
            icon.isPresent() ? 0xff404040 : 0xffffffff, icon.isEmpty());
    }

    public int[] measure(TextMeasure text) {
        return spriteElement().measure(text);
    }

    public Set<ValueType<?>> valueKinds(WidgetTypes widgets) {
        return NUMBERS;
    }

    public void validate() {
        SynchronizedElement.super.validate();
        ValueContract.require(this, "a <progress>", true);
        if (processorKey.filter(String::isBlank).isPresent())
            throw new IllegalArgumentException(
                "processor-key must not be blank");
    }

    public Common common() {
        return common;
    }

    public Optional<ResourceLocation> icon() {
        return icon;
    }

    public ProgressDirection direction() {
        return direction;
    }

    public Optional<String> processorKey() {
        return processorKey;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null || getClass() != object.getClass())
            return false;
        var other = (ProgressElement) object;
        return java.util.Objects.equals(common, other.common)
            && java.util.Objects.equals(icon, other.icon)
            && java.util.Objects.equals(direction, other.direction)
            && java.util.Objects.equals(processorKey, other.processorKey);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(common);
        hash = 31 * hash + java.util.Objects.hashCode(icon);
        hash = 31 * hash + java.util.Objects.hashCode(direction);
        hash = 31 * hash + java.util.Objects.hashCode(processorKey);
        return hash;
    }

    @Override
    public String toString() {
        return "ProgressElement[common=" + common + ", icon=" + icon
            + ", direction=" + direction + ", processorKey=" + processorKey
            + "]";
    }
}
