package zone.moddev.mc.lib.gui.model.elements;

import java.util.List;
import java.util.Optional;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.sync.ElementSync;
import zone.moddev.mc.lib.gui.sync.SynchronizedElement;
import zone.moddev.mc.lib.machine.feature.Processor;
import zone.moddev.mc.lib.machine.feature.WorkingArea;

/**
 * A client-local display toggle; the server supplies the actual working bounds.
 */
public class WorkingAreaButtonElement
    implements ButtonControl, SynchronizedElement {
    public static final ElementSync<CompoundTag> AREA = ElementSync
        .field(ResourceLocation.parse("mmdlib:working_area"),
            ByteBufCodecs.COMPOUND_TAG.<RegistryFriendlyByteBuf>cast(), 1024,
            element -> new CompoundTag())
        .sample(
            (source, previous) -> ((WorkingAreaButtonElement) source.element())
                .sample((ScreenHost) source.machine()))
        .build();

    public List<ElementSync<?>> synchronizedValues() {
        return List.of(AREA);
    }
    private final Common common;
    private final Optional<String> processor;

    public WorkingAreaButtonElement(Common common, Optional<String> processor) {
        this.common = common;
        this.processor = processor;
    }

    public Common common() {
        return common;
    }
    public boolean flat() {
        return true;
    }
    public Optional<Text> text() {
        return Optional.empty();
    }
    public Optional<ResourceLocation> iconFor(Object value) {
        return Optional.empty();
    }
    public Optional<Text> labelFor(Object value) {
        return Optional.empty();
    }

    private Processor<?> provider(ScreenHost host) {
        if (host.screenFeatures() == null)
            throw new IllegalArgumentException(
                "Working-area button requires a processor");
        return host.screenFeatures().findProcessor(processor)
            .filter(WorkingArea.Provider.class::isInstance)
            .orElseThrow(() -> new IllegalArgumentException(
                "Processor does not provide a working area"));
    }

    public void validateHost(ScreenHost host) {
        provider(host);
    }

    private CompoundTag sample(ScreenHost host) {
        var entity = (BlockEntity) host;
        var selected = provider(host);
        var area = ((WorkingArea.Provider) selected).workingArea();
        var tag = new CompoundTag();
        tag.putString("dimension",
            entity.getLevel().dimension().location().toString());
        tag.putLong("origin", entity.getBlockPos().asLong());
        tag.putString("processor", selected.key());
        tag.putBoolean("available", area != null);
        if (area != null) {
            var state = entity.getBlockState();
            var facing = state
                .hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                    ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                    : net.minecraft.core.Direction.NORTH;
            var bounds = area.bounds(entity.getBlockPos(), facing);
            tag.putInt("color",
                zone.moddev.mc.lib.machine.presentation.VisualizerStyle.of(host)
                    .rangeColor());
            tag.putDouble("minX", bounds.minX);
            tag.putDouble("minY", bounds.minY);
            tag.putDouble("minZ", bounds.minZ);
            tag.putDouble("maxX", bounds.maxX);
            tag.putDouble("maxY", bounds.maxY);
            tag.putDouble("maxZ", bounds.maxZ);
        }
        return tag;
    }

    public String toString() {
        return "WorkingAreaButton[" + common + "," + processor + "]";
    }
}
