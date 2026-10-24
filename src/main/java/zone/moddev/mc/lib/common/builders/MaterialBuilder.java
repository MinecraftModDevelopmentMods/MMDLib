package zone.moddev.mc.lib.common.builders;

import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.common.types.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable material specifications; the factory applies them to content templates. */
public final class MaterialBuilder extends Builder<MMDMaterial, MaterialBuilder> {
    public static final MaterialBuilder INSTANCE = new MaterialBuilder(Map.of());
    private enum Key { NAME, PARENT, PROPERTIES, SELECTION, EVENTS }
    private final Map<Key, Object> values;
    private MaterialBuilder(Map<Key, Object> values) { this.values = Map.copyOf(values); }
    public static MaterialBuilder create() { return INSTANCE; }
    @SuppressWarnings("unchecked") private <T> T value(Key key, T fallback) { return (T) values.getOrDefault(key, fallback); }
    private MaterialBuilder with(Key key, Object value) {
        var copy = new EnumMap<Key, Object>(Key.class); copy.putAll(values);
        copy.put(key, Objects.requireNonNull(value)); return new MaterialBuilder(copy);
    }
    @Override public MaterialBuilder setName(ResourceLocation name) { return with(Key.NAME, name); }
    @Override public MaterialBuilder setName(String name) { return setName(ResourceLocation.parse(name)); }
    @Override public ResourceLocation getRegistryName() { return value(Key.NAME, null); }
    @Override public MaterialBuilder setParent(ResourceLocation parent) { return with(Key.PARENT, parent); }
    public MaterialBuilder setProperties(MaterialProperties properties) { return with(Key.PROPERTIES, properties); }
    public MaterialProperties getProperties() { return value(Key.PROPERTIES, MaterialProperties.DEFAULT); }
    public MaterialBuilder setSelection(MaterialSelection selection) { return with(Key.SELECTION, selection); }
    public MaterialSelection getSelection() { return value(Key.SELECTION, MaterialSelection.DEFAULT); }
    public MaterialBuilder setParts(MaterialParts parts) { return setSelection(MaterialSelection.fromParts(parts)); }
    public MaterialBuilder setItems(String expression) {
        var s = getSelection(); return setSelection(new MaterialSelection(PartSelection.parse(expression, PartSelection.ITEMS), s.blocks(), s.entities(), s.hasFluid()));
    }
    public MaterialBuilder setItems(String... tokens) { return setItems(String.join(" ", tokens)); }
    public MaterialBuilder setBlocks(String expression) {
        var s = getSelection(); return setSelection(new MaterialSelection(s.items(), PartSelection.parse(expression, PartSelection.BLOCKS), s.entities(), s.hasFluid()));
    }
    public MaterialBuilder setBlocks(String... tokens) { return setBlocks(String.join(" ", tokens)); }
    public MaterialBuilder setEntities(String expression) {
        var s = getSelection(); return setSelection(new MaterialSelection(s.items(), s.blocks(), PartSelection.parse(expression, PartSelection.ENTITIES), s.hasFluid()));
    }
    public MaterialBuilder setEntities(String... tokens) { return setEntities(String.join(" ", tokens)); }
    public MaterialBuilder setHasFluid(boolean enabled) {
        var s = getSelection(); return setSelection(new MaterialSelection(s.items(), s.blocks(), s.entities(), enabled));
    }
    private MaterialBuilder edit(java.util.function.Function<MaterialProperties, MaterialProperties> operation) {
        return setProperties(operation.apply(getProperties()));
    }
    public MaterialBuilder setType(String type) {
        return edit(p -> new MaterialProperties(type, p.color(), p.hardness(), p.strength(), p.magic(), p.damage(), p.harvestTags(), p.special()));
    }
    public MaterialBuilder setColor(int argb) {
        return edit(p -> new MaterialProperties(p.type(), argb, p.hardness(), p.strength(), p.magic(), p.damage(), p.harvestTags(), p.special()));
    }
    public MaterialBuilder setHardness(double hardness) {
        return edit(p -> new MaterialProperties(p.type(), p.color(), hardness, p.strength(), p.magic(), p.damage(), p.harvestTags(), p.special()));
    }
    public MaterialBuilder setStrength(double strength) {
        return edit(p -> new MaterialProperties(p.type(), p.color(), p.hardness(), strength, p.magic(), p.damage(), p.harvestTags(), p.special()));
    }
    public MaterialBuilder setMagic(double magic) {
        return edit(p -> new MaterialProperties(p.type(), p.color(), p.hardness(), p.strength(), magic, p.damage(), p.harvestTags(), p.special()));
    }
    public MaterialBuilder setDamage(double damage) {
        return edit(p -> new MaterialProperties(p.type(), p.color(), p.hardness(), p.strength(), p.magic(), damage, p.harvestTags(), p.special()));
    }
    public MaterialBuilder setHarvestTags(Set<ResourceLocation> tags) {
        return edit(p -> new MaterialProperties(p.type(), p.color(), p.hardness(), p.strength(), p.magic(), p.damage(), tags, p.special()));
    }
    public MaterialBuilder setSpecial(Map<String, Double> special) {
        return edit(p -> new MaterialProperties(p.type(), p.color(), p.hardness(), p.strength(), p.magic(), p.damage(), p.harvestTags(), special));
    }
    @Override public MaterialBuilder setEventMap(Map<String, ResourceLocation> events) {
        if (events.keySet().stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("Event name must not be blank");
        return with(Key.EVENTS, Map.copyOf(events));
    }
    public static MaterialBuilder from(MMDMaterial material) {
        var builder = create().setProperties(material.properties()).setSelection(material.selection()).setEventMap(material.events());
        if (material.name().isPresent()) builder = builder.setName(material.name().get());
        if (material.parent().isPresent()) builder = builder.setParent(material.parent().get());
        return builder;
    }
    @Override public MMDMaterial build() {
        if (getRegistryName() == null) throw new IllegalStateException("Material needs a name");
        return new MMDMaterial(Optional.of(getRegistryName()), Optional.ofNullable(value(Key.PARENT, null)),
                getProperties(), getSelection(), value(Key.EVENTS, Map.of()));
    }
}
