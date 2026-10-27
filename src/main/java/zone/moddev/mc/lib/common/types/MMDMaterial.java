package zone.moddev.mc.lib.common.types;

import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.common.builders.MaterialContentPlanner;
import zone.moddev.mc.lib.common.factories.MaterialFactory;
import zone.moddev.mc.lib.util.DataLoaders;
import java.io.StringReader;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/** Immutable material definition. File-backed definitions can acquire their name from the factory. */
public record MMDMaterial(Optional<ResourceLocation> name, Optional<ResourceLocation> parent,
        MaterialProperties properties, MaterialSelection selection, Map<String, ResourceLocation> events, MaterialRuntimeSettings runtime) {
    public MMDMaterial(Optional<ResourceLocation> name, Optional<ResourceLocation> parent,
            MaterialProperties properties, MaterialSelection selection, Map<String, ResourceLocation> events) {
        this(name, parent, properties, selection, events, MaterialRuntimeSettings.DEFAULT);
    }
    public MMDMaterial {
        java.util.Objects.requireNonNull(name); java.util.Objects.requireNonNull(parent);
        java.util.Objects.requireNonNull(properties); java.util.Objects.requireNonNull(selection);
        java.util.Objects.requireNonNull(runtime);
        events = Map.copyOf(events);
        if (events.keySet().stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("Event name must not be blank");
    }
    public MMDMaterial() { this(MaterialParts.DEFAULT); }
    public MMDMaterial(MaterialParts parts) {
        this(Optional.empty(), Optional.empty(), MaterialProperties.DEFAULT, MaterialSelection.fromParts(parts), Map.of());
    }
    public MaterialParts parts() { return selection.parts(); }
    public MaterialContentPlan planContent(ResourceLocation id) {
        checkName(id);
        return new MaterialFactory().create(zone.moddev.mc.lib.common.builders.MaterialBuilder.from(this).setName(id).build()).content();
    }
    public MaterialContentPlan planContent(ResourceLocation id, MaterialContentPlanner planner) {
        checkName(id); return planner.plan(id, parts());
    }
    public MaterialContentPlan planContent(ResourceLocation id, MaterialContentPlanner planner, Predicate<String> installed) {
        checkName(id); return planner.plan(id, parts(), installed);
    }
    private void checkName(ResourceLocation id) {
        if (name.isPresent() && !name.get().equals(id)) throw new IllegalArgumentException("Material name disagrees with " + id);
    }
    /** Compatibility entrypoint: raw material JSON, not the old unfinished JSLT transformation. */
    public MMDMaterial load(ResourceLocation id) {
        return new MaterialFactory().decode(id, new StringReader(DataLoaders.loadFile(id, "json", LocationType.MATERIAL)));
    }
}
