package zone.moddev.mc.lib.common.types;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.UseAnim;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Material-bound item definition. Building this does not register a Minecraft item. */
public record MMDItem(ResourceLocation name, ResourceLocation template, ResourceLocation material,
        ResourceLocation parent, String type, int maxStackSize, int maxDamage, boolean fireResistant,
        ResourceLocation food, ResourceLocation containerItem, int useTime, UseAnim useAnim,
        ResourceLocation useFinishMode, String colorHandler, List<String> lore,
        Set<String> toolActions, Map<String, ResourceLocation> events, List<CreativeStack> creativeStacks, List<AttributeModifierDefinition> attributeModifiers) {
    public MMDItem {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(useAnim, "useAnim");
        if (maxStackSize < 1 || maxDamage < 0 || useTime < 0)
            throw new IllegalArgumentException("Invalid item size, durability, or use time");
        if (maxDamage > 0 && maxStackSize != 1)
            throw new IllegalArgumentException("Damageable items must have a stack size of one");
        lore = List.copyOf(lore);
        toolActions = Set.copyOf(toolActions);
        events = Map.copyOf(events);
        creativeStacks = List.copyOf(creativeStacks);
        attributeModifiers = List.copyOf(attributeModifiers);
    }

    /** Snapshot of a creative stack; null item means the material's constructed item. */
    public record CreativeStack(ResourceLocation item, CompoundTag tag, int count, List<String> tabs) {
        public CreativeStack {
            if (count < 1) throw new IllegalArgumentException("Stack count must be positive");
            tag = tag == null ? null : tag.copy();
            tabs = List.copyOf(tabs);
            if (tabs.isEmpty() || tabs.stream().anyMatch(String::isBlank))
                throw new IllegalArgumentException("Creative stacks require nonempty tab names");
        }
        @Override public CompoundTag tag() { return tag == null ? null : tag.copy(); }
    }
}
