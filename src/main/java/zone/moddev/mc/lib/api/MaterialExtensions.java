package zone.moddev.mc.lib.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import zone.moddev.mc.lib.common.types.*;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Register code extensions before decoding material definitions that reference them. */
public final class MaterialExtensions {
    public static final MaterialExtensions GLOBAL = new MaterialExtensions();
    public record ItemType(ItemTypeFactory<?> factory, Optional<String> requiredMod) {}
    public record BlockType(BlockTypeFactory<?> factory, Optional<String> requiredMod) {}
    public record Snapshot(Map<ResourceLocation, ItemType> items, Map<ResourceLocation, BlockType> blocks) {}
    private final ConcurrentHashMap<ResourceLocation, ItemType> items = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ResourceLocation, BlockType> blocks = new ConcurrentHashMap<>();
    private final MaterialEvents events = new MaterialEvents();
    public MaterialEvents events() { return events; }
    public <I extends Item> void registerItemType(ResourceLocation id, ItemTypeFactory<I> factory) {
        registerItemType(id, factory, Optional.empty());
    }
    public <I extends Item> void registerItemType(ResourceLocation id, ItemTypeFactory<I> factory, Optional<String> requiredMod) {
        checkId(id); Objects.requireNonNull(factory.itemClass()); checkMod(requiredMod);
        if (items.putIfAbsent(id, new ItemType(factory, requiredMod)) != null) throw new IllegalArgumentException("Item type already registered: " + id);
    }
    public <B extends Block> void registerBlockType(ResourceLocation id, BlockTypeFactory<B> factory) {
        registerBlockType(id, factory, Optional.empty());
    }
    public <B extends Block> void registerBlockType(ResourceLocation id, BlockTypeFactory<B> factory, Optional<String> requiredMod) {
        checkId(id); Objects.requireNonNull(factory.blockClass()); checkMod(requiredMod);
        if (blocks.putIfAbsent(id, new BlockType(factory, requiredMod)) != null) throw new IllegalArgumentException("Block type already registered: " + id);
    }
    private static void checkMod(Optional<String> mod) {
        Objects.requireNonNull(mod);
        if (mod.isPresent() && !mod.get().matches("[a-z][a-z0-9_]{1,63}")) throw new IllegalArgumentException("Invalid required mod ID");
    }
    private static void checkId(ResourceLocation id) {
        Objects.requireNonNull(id);
        if (id.getNamespace().equals("mmdlib") && MaterialPart.fromName(id.getPath()).result().isPresent())
            throw new IllegalArgumentException("Cannot replace a standard part: " + id);
    }
    public Snapshot snapshot() { return new Snapshot(Map.copyOf(items), Map.copyOf(blocks)); }
    public MaterialContentPlan extend(MMDMaterial material, MaterialContentPlan base, Predicate<String> installed) {
        var itemDefinitions = new java.util.LinkedHashMap<ResourceLocation, MMDItem>();
        var blockDefinitions = new java.util.LinkedHashMap<ResourceLocation, MMDBlock>();
        for (var id : material.selection().customItems().stream().sorted().toList()) {
            var entry = items.get(id);
            if (entry == null) throw new IllegalArgumentException("Unknown item type: " + id);
            if (entry.requiredMod().isPresent() && !installed.test(entry.requiredMod().get())) continue;
            itemDefinitions.put(id, Objects.requireNonNull(entry.factory().template(material)).setName(id)
                    .setType(id.toString()).forMaterial(base.material()).build());
        }
        for (var id : material.selection().customBlocks().stream().sorted().toList()) {
            var entry = blocks.get(id);
            if (entry == null) throw new IllegalArgumentException("Unknown block type: " + id);
            if (entry.requiredMod().isPresent() && !installed.test(entry.requiredMod().get())) continue;
            var block = Objects.requireNonNull(entry.factory().template(material)).setName(id)
                    .setType(id.toString()).forMaterial(base.material()).build();
            blockDefinitions.put(id, block);
            if (block.item() != null && itemDefinitions.putIfAbsent(id, block.item()) != null)
                throw new IllegalArgumentException("Item type conflicts with block item: " + id);
        }
        var itemIds = new java.util.HashSet<ResourceLocation>();
        base.items().values().forEach(item -> itemIds.add(item.name()));
        if (base.fluid() != null && base.fluid().bucket() != null) itemIds.add(base.fluid().bucket().name());
        for (var item : itemDefinitions.values()) if (!itemIds.add(item.name())) throw new IllegalArgumentException("Duplicate item ID: " + item.name());
        var blockIds = new java.util.HashSet<ResourceLocation>();
        base.blocks().values().forEach(block -> blockIds.add(block.name()));
        if (base.fluid() != null && base.fluid().block() != null) blockIds.add(base.fluid().block().name());
        for (var block : blockDefinitions.values()) if (!blockIds.add(block.name())) throw new IllegalArgumentException("Duplicate block ID: " + block.name());
        return new MaterialContentPlan(base.material(), base.items(), base.blocks(), base.entities(), base.fluid(), itemDefinitions, blockDefinitions);
    }
    @SuppressWarnings("unchecked")
    private <I extends Item> ItemTypeFactory<I> itemFactory(ResourceLocation id, Class<I> expected) {
        var entry = items.get(id);
        if (entry == null || entry.factory().itemClass() != expected) throw new IllegalArgumentException("Unknown item type or wrong class: " + id);
        return (ItemTypeFactory<I>) entry.factory();
    }
    @SuppressWarnings("unchecked")
    private <B extends Block> BlockTypeFactory<B> blockFactory(ResourceLocation id, Class<B> expected) {
        var entry = blocks.get(id);
        if (entry == null || entry.factory().blockClass() != expected) throw new IllegalArgumentException("Unknown block type or wrong class: " + id);
        return (BlockTypeFactory<B>) entry.factory();
    }
    public <I extends Item> DeferredHolder<Item, I> queueItem(DeferredRegister<Item> registry, ResourceLocation type,
            Class<I> expected, MMDMaterial material, MMDItem definition) {
        var factory = itemFactory(type, expected);
        checkDefinition(registry.getNamespace(), material, definition.name(), definition.material(), definition.type(), type);
        return registry.register(definition.name().getPath(), () -> expected.cast(Objects.requireNonNull(factory.create(material, definition))));
    }
    public <B extends Block> DeferredHolder<Block, B> queueBlock(DeferredRegister<Block> registry, ResourceLocation type,
            Class<B> expected, MMDMaterial material, MMDBlock definition) {
        var factory = blockFactory(type, expected);
        checkDefinition(registry.getNamespace(), material, definition.name(), definition.material(), definition.type(), type);
        return registry.register(definition.name().getPath(), () -> expected.cast(Objects.requireNonNull(factory.create(material, definition))));
    }
    private static void checkDefinition(String namespace, MMDMaterial material, ResourceLocation name,
            ResourceLocation owner, String declaredType, ResourceLocation type) {
        if (!name.getNamespace().equals(namespace) || !material.name().orElseThrow().equals(owner) || !declaredType.equals(type.toString()))
            throw new IllegalArgumentException("Definition does not belong to this registry, material, or type: " + name);
    }
}
