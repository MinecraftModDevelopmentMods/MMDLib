package zone.moddev.mc.lib.gui.model.elements;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.api.entity.EntitySnapshot;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.ProcessorVariable;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.sync.ElementSync;
import zone.moddev.mc.lib.gui.sync.SynchronizedElement;
import zone.moddev.mc.lib.machine.feature.Processor;

/**
 * Standard portrait of saved living-entity data from a stored processor
 * variable.
 */
public class EntityPortraitElement implements SynchronizedElement {
    public static final ElementSync<EntitySnapshot> ENTITY = ElementSync
        .field(ResourceLocation.parse("mmdlib:entity_portrait"),
            ByteBufCodecs.COMPOUND_TAG.<RegistryFriendlyByteBuf>cast()
                .map(EntitySnapshot::new, EntitySnapshot::data),
            ElementSync.MAX_BYTES, element -> EntitySnapshot.EMPTY)
        .sample((source, previous) -> ((EntityPortraitElement) source.element())
            .read((ScreenHost) source.machine()))
        .build();
    private final Common common;
    private final ProcessorVariable variable;
    private final int width, height;
    private final Optional<Text> emptyText;

    public EntityPortraitElement(Common common, String variable, int width,
        int height, Optional<Text> emptyText) {
        this.common = Objects.requireNonNull(common);
        this.variable = ProcessorVariable.parse(variable);
        if (width < 1 || width > 1024 || height < 1 || height > 1024)
            throw new IllegalArgumentException(
                "Portrait dimensions must be 1..1024 pixels");
        this.width = width;
        this.height = height;
        this.emptyText = Objects.requireNonNull(emptyText);
        if (common.value().isPresent() || common.max().isPresent())
            throw new IllegalArgumentException(
                "Entity portrait uses variable, not value/max getters");
    }
    public Common common() {
        return common;
    }
    public ProcessorVariable variable() {
        return variable;
    }
    public Optional<Text> emptyText() {
        return emptyText;
    }
    public int[] measure(TextMeasure measure) {
        return new int[]{width, height};
    }
    public List<ElementSync<?>> synchronizedValues() {
        return List.of(ENTITY);
    }
    private EntitySnapshot checked(Object value) {
        if (!(value instanceof EntitySnapshot data))
            throw new IllegalArgumentException(
                "Entity portrait variable must be EntitySnapshot: " + variable);
        return data;
    }
    public EntitySnapshot read(ScreenHost host) {
        return checked(variable.read(host));
    }
    public void validateDefinition(Map<String, Processor<?>> processors) {
        checked(variable.defaultValue(processors));
    }
    public void validateHostType(Class<?> owner) {
        if (!ScreenHost.class.isAssignableFrom(owner))
            throw new IllegalArgumentException(
                "Entity portrait requires a ScreenHost");
    }
    public void validateHost(ScreenHost host) {
        read(host);
    }
    @Override
    public boolean equals(Object other) {
        if (other == null || other.getClass() != getClass())
            return false;
        var portrait = (EntityPortraitElement) other;
        return common.equals(portrait.common)
            && variable.equals(portrait.variable) && width == portrait.width
            && height == portrait.height
            && emptyText.equals(portrait.emptyText);
    }
    @Override
    public int hashCode() {
        return Objects.hash(common, variable, width, height, emptyText);
    }
    @Override
    public String toString() {
        return "EntityPortraitElement[common=" + common + ",variable="
            + variable + ",width=" + width + ",height=" + height + ",emptyText="
            + emptyText + "]";
    }
}
