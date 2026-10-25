package zone.moddev.mc.lib.common.modules;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import zone.moddev.mc.lib.api.*;
import zone.moddev.mc.lib.common.builders.*;
import zone.moddev.mc.lib.common.types.*;

/** Adapts every data module through the existing type API, regardless of who supplied its JSON. */
public final class ModuleFactories {
    private ModuleFactories() {}
    public interface Reference { ResourceLocation implementation(); MaterialPart part(); }
    public static final class ItemFactory implements ItemTypeFactory<Item>, Reference {
        private final MaterialExtensions api; private final ResourceLocation id; private final DataModules.Item data;
        public ItemFactory(MaterialExtensions api, ResourceLocation id, DataModules.Item data) { this.api=api; this.id=id; this.data=data; }
        @Override public ResourceLocation implementation() { return data.factory(); }
        @Override public MaterialPart part() { return data.definition(id); }
        @SuppressWarnings("unchecked") @Override public Class<Item> itemClass() { return (Class<Item>)(Class<?>) api.itemImplementation(data.factory()).itemClass(); }
        @Override public ItemBuilder template(MMDMaterial material) { return data.builder(id, material); }
        @Override public Item create(MMDMaterial material, MMDItem definition) { return api.itemImplementation(data.factory()).create(material, definition, data.factoryData()); }
    }
    public static final class BlockFactory implements BlockTypeFactory<Block>, Reference {
        private final MaterialExtensions api; private final ResourceLocation id; private final DataModules.Block data;
        public BlockFactory(MaterialExtensions api, ResourceLocation id, DataModules.Block data) { this.api=api; this.id=id; this.data=data; }
        @Override public ResourceLocation implementation() { return data.factory(); }
        @Override public MaterialPart part() { return data.definition(id); }
        @SuppressWarnings("unchecked") @Override public Class<Block> blockClass() { return (Class<Block>)(Class<?>) api.blockImplementation(data.factory()).blockClass(); }
        @Override public BlockBuilder template(MMDMaterial material) { return data.builder(id, material); }
        @Override public Block create(MMDMaterial material, MMDBlock definition) { return api.blockImplementation(data.factory()).create(material, definition, data.factoryData()); }
    }
}
