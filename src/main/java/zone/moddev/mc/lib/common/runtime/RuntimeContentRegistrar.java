package zone.moddev.mc.lib.common.runtime;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.registries.*;
import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.common.factories.MaterialFactory;
import zone.moddev.mc.lib.common.types.*;
import java.util.*;
import java.util.function.Supplier;

/** Reusable runtime application of loaded material plans. Membership and material settings come from data. */
public final class RuntimeContentRegistrar {
    private static final List<RuntimeContentRegistrar> INSTANCES = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final String namespace;
    private final MaterialExtensions extensions;
    private final DeferredRegister<Block> blocks;
    private final DeferredRegister<Item> items;
    private final DeferredRegister<Fluid> fluids;
    private final DeferredRegister<FluidType> fluidTypes;
    private final DeferredRegister<EntityType<?>> entities;
    private final DeferredRegister<ArmorMaterial> armor;
    private final DeferredRegister<CreativeModeTab> tabs;
    private final Map<ResourceLocation, DeferredHolder<Block, ? extends Block>> blockEntries = new TreeMap<>();
    private final Map<ResourceLocation, DeferredHolder<Item, ? extends Item>> itemEntries = new TreeMap<>();
    private final Map<ResourceLocation, DeferredHolder<EntityType<?>, EntityType<MaterialProjectile>>> projectiles = new TreeMap<>();
    private final Map<ResourceLocation, MMDMaterial> itemMaterials = new TreeMap<>(), blockMaterials = new TreeMap<>();
    private final List<FluidFamily> fluidFamilies = new ArrayList<>();
    public RuntimeContentRegistrar(String namespace, MaterialExtensions extensions, IEventBus bus) {
        this.namespace = namespace; this.extensions = extensions;
        blocks = DeferredRegister.create(Registries.BLOCK, namespace); items = DeferredRegister.create(Registries.ITEM, namespace);
        fluids = DeferredRegister.create(Registries.FLUID, namespace); fluidTypes = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, namespace);
        entities = DeferredRegister.create(Registries.ENTITY_TYPE, namespace); armor = DeferredRegister.create(Registries.ARMOR_MATERIAL, namespace);
        tabs = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, namespace);
        blocks.register(bus); items.register(bus); fluids.register(bus); fluidTypes.register(bus); entities.register(bus); armor.register(bus); tabs.register(bus);
        tabs.register("materials", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup." + namespace + ".materials"))
                .icon(() -> itemEntries.isEmpty() ? new ItemStack(Items.IRON_INGOT) : new ItemStack(itemEntries.values().iterator().next().get()))
                .displayItems((parameters, output) -> itemEntries.values().forEach(item -> output.accept(item.get()))).build());
        INSTANCES.add(this);
    }
    public static List<RuntimeContentRegistrar> instances() { return List.copyOf(INSTANCES); }
    public Map<ResourceLocation, DeferredHolder<Item, ? extends Item>> items() { return Collections.unmodifiableMap(itemEntries); }
    public Map<ResourceLocation, DeferredHolder<Block, ? extends Block>> blocks() { return Collections.unmodifiableMap(blockEntries); }
    public Map<ResourceLocation, MMDMaterial> itemMaterials() { return Collections.unmodifiableMap(itemMaterials); }
    public Map<ResourceLocation, MMDMaterial> blockMaterials() { return Collections.unmodifiableMap(blockMaterials); }
    public Map<ResourceLocation, DeferredHolder<EntityType<?>, EntityType<MaterialProjectile>>> projectiles() { return Collections.unmodifiableMap(projectiles); }
    public List<FluidFamily> fluidFamilies() { return List.copyOf(fluidFamilies); }
    public void queue(MaterialFactory.Result result) {
        var material = result.material(); var plan = result.content(); var owner = material.name().orElseThrow();
        if (!namespace.equals(owner.getNamespace())) throw new IllegalArgumentException("Runtime registry namespace differs from " + owner);
        if (!plan.blockEntities().isEmpty()) throw new IllegalArgumentException("Machine block factory must provide block-entity runtime wiring: " + owner);
        Supplier<Ingredient> repair = () -> material.runtime().repairItem().map(id -> Ingredient.of(RuntimeProperties.required(BuiltInRegistries.ITEM, id))).orElse(Ingredient.EMPTY);
        var tier = new MaterialToolTier(material, TagKey.create(Registries.BLOCK, material.runtime().incorrectBlocks()), repair);
        boolean needsArmor = plan.items().entrySet().stream().anyMatch(entry -> Set.of("mmdlib:armor", "mmdlib:horse_armor").contains(entry.getKey().factory().toString()));
        Holder<ArmorMaterial> armorHolder = needsArmor ? MaterialArmor.register(armor, material, repair, material.runtime().armorTexture()) : null;
        plan.entities().forEach((part, definition) -> {
            if (!Set.of("mmdlib:arrow", "mmdlib:bolt").contains(definition.factory().toString()))
                throw new IllegalArgumentException("Use the registered EntityFactory for custom world entities: " + definition.factory());
            var type = entities.register(definition.name().getPath(), () -> EntityType.Builder.<MaterialProjectile>of(MaterialProjectile::new, definition.category())
                    .sized(definition.width(), definition.height()).clientTrackingRange(definition.trackingRange()).updateInterval(definition.updateInterval()).build(definition.name().toString()));
            projectiles.put(definition.name(), type);
        });
        var orderedBlocks = plan.blocks().entrySet().stream().sorted(java.util.Comparator
                .<Map.Entry<MaterialPart, MMDBlock>>comparingInt(entry -> entry.getValue().name().equals(material.runtime().baseBlock()) ? 0 : 1)
                .thenComparing(entry -> entry.getValue().name())).toList();
        orderedBlocks.forEach(entry -> {
            var part = entry.getKey(); var definition = entry.getValue();
            var block = blocks.register(definition.name().getPath(), () -> createBlock(part, definition, material));
            put(blockEntries, definition.name(), block); blockMaterials.put(definition.name(), material);
            if (definition.item() != null) {
                var item = items.register(definition.name().getPath(), () -> createBlockItem(block.get(), definition.item(), material, part));
                put(itemEntries, definition.name(), item); itemMaterials.put(definition.name(), material);
            }
        });
        plan.items().forEach((part, definition) -> {
            if (part.kind() == MaterialPart.Kind.BLOCK) return; // Already queued as the block's actual BlockItem.
            extensions.events().validate(definition.events()); // Fail at load, not during registry construction.
            StandardItems.validate(part);
            var item = items.register(definition.name().getPath(), () -> {
                var properties = RuntimeProperties.item(definition);
                if (material.runtime().tintedArmor() && Set.of("mmdlib:armor", "mmdlib:horse_armor").contains(part.factory().toString()))
                    properties.component(net.minecraft.core.component.DataComponents.DYED_COLOR,
                            new net.minecraft.world.item.component.DyedItemColor(material.properties().color() & 0xFFFFFF, false));
                var implementation = extensions.findItemImplementation(part.factory());
                if (implementation.isPresent()) return implementation.get().create(material, definition, part.factoryData());
                if (part.needsEntity()) return new MaterialAmmoItem(material, projectiles.get(definition.name()), properties);
                if (part.factory().toString().equals("mmdlib:basic_item")) return new FuelItem(material,
                        new MaterialStats(material).integer("fuel_" + part.templateId().getPath(), new MaterialStats(material).integer("item_burn_time", 0, 0), 0), properties);
                if (part.factory().getNamespace().equals("mmdlib")) return StandardItems.create(part, tier, armorHolder, properties);
                return extensions.itemImplementation(part.factory()).create(material, definition, part.factoryData());
            });
            put(itemEntries, definition.name(), item); itemMaterials.put(definition.name(), material);
        });
        if (plan.fluid() != null) fluidFamilies.add(new FluidFamily(plan.fluid(), material));
    }
    private Block createBlock(MaterialPart part, MMDBlock definition, MMDMaterial material) {
        var implementation = extensions.findBlockImplementation(part.factory());
        if (implementation.isPresent()) return implementation.get().create(material, definition, part.factoryData());
        var properties = RuntimeProperties.block(definition, material.runtime(), false);
        var setId = material.runtime().blockSetType();
        if (!Set.of("mmdlib:door", "mmdlib:trapdoor").contains(part.factory().toString())) {
            var json = part.factoryData().json();
            if (json.isJsonObject() && json.getAsJsonObject().has("block_set_type")) setId = ResourceLocation.parse(json.getAsJsonObject().get("block_set_type").getAsString());
        }
        var set = setId;
        var setType = BlockSetType.values().filter(value -> ResourceLocation.parse(value.name()).equals(set)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown block set type: " + set));
        Supplier<BlockState> base = () -> RuntimeProperties.required(BuiltInRegistries.BLOCK, material.runtime().baseBlock()).defaultBlockState();
        return StandardBlocks.create(part, properties, setType, WoodType.OAK, base);
    }
    private Item createBlockItem(Block block, MMDItem definition, MMDMaterial material, MaterialPart part) {
        var properties = RuntimeProperties.item(definition);
        if (part.factory().toString().equals("mmdlib:door")) return new DoubleHighBlockItem(block, properties);
        int fuel = new MaterialStats(material).integer("block_burn_time", 0, 0);
        return new BlockItem(block, properties) {
            @Override public int getBurnTime(ItemStack stack, RecipeType<?> recipe) { return fuel; }
        };
    }
    private static <T> void put(Map<ResourceLocation, T> map, ResourceLocation id, T value) {
        if (map.putIfAbsent(id, value) != null) throw new IllegalArgumentException("Duplicate runtime entry: " + id);
    }
    private static final class FuelItem extends StandardItems.Basic {
        private final int burn;
        FuelItem(MMDMaterial material, int burn, Item.Properties properties) { super(material, () -> Ingredient.EMPTY, properties); this.burn = burn; }
        @Override public int getBurnTime(ItemStack stack, RecipeType<?> recipe) { return burn; }
    }
    public final class FluidFamily {
        public final MMDFluid definition;
        public final DeferredHolder<FluidType, FluidType> type;
        public final DeferredHolder<Fluid, BaseFlowingFluid.Source> source;
        public final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing;
        public final DeferredHolder<Block, LiquidBlock> block;
        public final DeferredHolder<Item, BucketItem> bucket;
        FluidFamily(MMDFluid definition, MMDMaterial material) {
            this.definition = definition;
            if (!definition.factory().toString().equals("mmdlib:flowing_fluid")) throw new IllegalArgumentException("Unknown fluid implementation: " + definition.factory());
            type = fluidTypes.register(definition.typeId().getPath(), () -> {
                var p = definition.typeProperties();
                return new FluidType(FluidType.Properties.create().density(p.density()).viscosity(p.viscosity()).temperature(p.temperature())
                        .lightLevel(p.lightLevel()).motionScale(p.motionScale()).canSwim(p.canSwim()).canDrown(p.canDrown()).canExtinguish(p.canExtinguish())
                        .supportsBoating(p.supportsBoating()).canHydrate(p.canHydrate()).canConvertToSource(definition.flowProperties().canConvertToSource())) {};
            });
            source = fluids.register(definition.sourceId().getPath(), () -> new BaseFlowingFluid.Source(properties()));
            flowing = fluids.register(definition.flowingId().getPath(), () -> new BaseFlowingFluid.Flowing(properties()));
            block = definition.block() == null ? null : blocks.register(definition.blockId().getPath(), () ->
                    new LiquidBlock(source.get(), RuntimeProperties.block(definition.block(), material.runtime(), true)));
            bucket = definition.bucket() == null ? null : items.register(definition.bucketId().getPath(), () -> new BucketItem(source.get(), RuntimeProperties.item(definition.bucket())));
            if (block != null) put(blockEntries, definition.blockId(), block);
            if (bucket != null) { put(itemEntries, definition.bucketId(), bucket); itemMaterials.put(definition.bucketId(), material); }
        }
        private BaseFlowingFluid.Properties properties() {
            var flow = definition.flowProperties();
            var properties = new BaseFlowingFluid.Properties(type, () -> source.get(), () -> flowing.get())
                    .slopeFindDistance(flow.slopeFindDistance()).levelDecreasePerBlock(flow.levelDecreasePerBlock()).tickRate(flow.tickRate()).explosionResistance(flow.explosionResistance());
            if (block != null) properties.block(block);
            if (bucket != null) properties.bucket(bucket);
            return properties;
        }
    }
}
