package zone.moddev.mc.lib.common.types;

import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Predicate;

/** A loaded form definition. Identity is its namespaced module ID, not a Java enum ordinal. */
public final class MaterialPart implements Comparable<MaterialPart> {
    public enum Kind { ITEM, BLOCK }

    private final ResourceLocation name;
    private final Kind kind;
    private final ResourceLocation factory;
    private final Optional<String> requiredMod;
    private final PartMetadata metadata;
    private final FactoryData factoryData;
    public MaterialPart(ResourceLocation name, Kind kind, Optional<String> requiredMod, PartMetadata metadata) {
        this(name, kind, requiredMod, metadata, name);
    }
    public MaterialPart(ResourceLocation name, Kind kind, Optional<String> requiredMod, PartMetadata metadata, ResourceLocation factory) {
        this(name, kind, requiredMod, metadata, factory, FactoryData.EMPTY);
    }
    public MaterialPart(ResourceLocation name, Kind kind, Optional<String> requiredMod, PartMetadata metadata,
                        ResourceLocation factory, FactoryData factoryData) {
        this.name = Objects.requireNonNull(name); this.kind = Objects.requireNonNull(kind);
        this.factoryData = Objects.requireNonNull(factoryData);
        this.factory = Objects.requireNonNull(factory);
        this.requiredMod = Objects.requireNonNull(requiredMod); this.metadata = Objects.requireNonNull(metadata);
        if (metadata.entityFactory().isEmpty() && !metadata.entityData().equals(FactoryData.EMPTY))
            throw new IllegalArgumentException("entity_data requires entity_factory: " + name);
        if (kind == Kind.ITEM && metadata.blockEntity().isPresent())
            throw new IllegalArgumentException("Block entities require a block module: " + name);
    }
    public ResourceLocation factory() { return factory; }
    public FactoryData factoryData() { return factoryData; }
    public Kind kind() { return kind; }
    public Optional<String> requiredMod() { return requiredMod; }
    public PartMetadata metadata() { return metadata; }
    public boolean available(Predicate<String> installed) { return requiredMod.isEmpty() || installed.test(requiredMod.get()); }
    public boolean needsEntity() { return metadata.entityFactory().isPresent(); }
    public Optional<ResourceLocation> entityFactory() { return metadata.entityFactory(); }
    public boolean needsBlockEntity() { return metadata.blockEntity().isPresent(); }
    public String id() { return name.getNamespace().equals("mmdlib") ? name.getPath() : name.toString(); }
    public ResourceLocation templateId() { return name; }
    @Override public int compareTo(MaterialPart other) { return name.compareTo(other.name); }
    @Override public boolean equals(Object other) { return other instanceof MaterialPart part && name.equals(part.name); }
    @Override public int hashCode() { return name.hashCode(); }
    @Override public String toString() { return name.toString(); }
}
