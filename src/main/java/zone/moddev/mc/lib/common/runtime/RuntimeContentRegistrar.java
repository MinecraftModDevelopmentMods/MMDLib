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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.registries.*;
import zone.moddev.mc.lib.api.BlockContext;
import zone.moddev.mc.lib.api.ItemContext;
import zone.moddev.mc.lib.api.MaterialEvents;
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
        // Every factory is looked up now, so an unregistered one fails at load. The armor material is registered
        // only when some part's implementation asks for it.
        boolean needsArmor = plan.items().keySet().stream().filter(part -> part.kind() == MaterialPart.Kind.ITEM)
                .anyMatch(part -> extensions.itemImplementation(part.factory()).usesArmorMaterial());
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
            var implementation = extensions.blockImplementation(part.factory());
            implementation.validate(part);
            var block = blocks.register(definition.name().getPath(), () -> implementation.create(new BlockContextImpl(part, definition, material, extensions.events())));
            put(blockEntries, definition.name(), block); blockMaterials.put(definition.name(), material);
            if (definition.item() != null) {
                var item = items.register(definition.name().getPath(), () -> implementation.createItem(block.get(),
                        new ItemContextImpl(part, definition.item(), material, tier, armorHolder)));
                put(itemEntries, definition.name(), item); itemMaterials.put(definition.name(), material);
            }
        });
        plan.items().forEach((part, definition) -> {
            if (part.kind() == MaterialPart.Kind.BLOCK) return; // Already queued as the block's actual BlockItem.
            extensions.events().validate(definition.events()); // Fail at load, not during registry construction.
            var implementation = extensions.itemImplementation(part.factory());
            implementation.validate(part);
            var item = items.register(definition.name().getPath(), () -> implementation.create(new ItemContextImpl(part, definition, material, tier, armorHolder)));
            put(itemEntries, definition.name(), item); itemMaterials.put(definition.name(), material);
        });
        if (plan.fluid() != null) fluidFamilies.add(new FluidFamily(plan.fluid(), material));
    }
    /** The registrar's {@link ItemContext}: fresh properties per call, shared tier and armor per material. */
    private final class ItemContextImpl implements ItemContext {
        private final MaterialPart part; private final MMDItem definition; private final MMDMaterial material;
        private final MaterialToolTier tier; private final Holder<ArmorMaterial> armor;
        ItemContextImpl(MaterialPart part, MMDItem definition, MMDMaterial material, MaterialToolTier tier, Holder<ArmorMaterial> armor) {
            this.part = part; this.definition = definition; this.material = material; this.tier = tier; this.armor = armor;
        }
        @Override public MMDMaterial material() { return material; }
        @Override public MMDItem definition() { return definition; }
        @Override public MaterialPart part() { return part; }
        @Override public Item.Properties properties() { return RuntimeProperties.item(definition); }
        @Override public MaterialToolTier tier() { return tier; }
        @Override public Holder<ArmorMaterial> armor() {
            if (armor == null) throw new IllegalStateException("Armor material not registered; the implementation must report usesArmorMaterial(): " + definition.name());
            return armor;
        }
        @Override public Supplier<EntityType<MaterialProjectile>> projectile() {
            var type = projectiles.get(definition.name());
            if (type == null) throw new IllegalStateException("No projectile registered for " + definition.name());
            return type;
        }
    }
    /** The registrar's {@link BlockContext}. */
    private static final class BlockContextImpl implements BlockContext {
        private final MaterialPart part; private final MMDBlock definition; private final MMDMaterial material; private final MaterialEvents events;
        BlockContextImpl(MaterialPart part, MMDBlock definition, MMDMaterial material, MaterialEvents events) {
            this.part = part; this.definition = definition; this.material = material; this.events = events;
        }
        @Override public MaterialEvents events() { return events; }
        @Override public MMDMaterial material() { return material; }
        @Override public MMDBlock definition() { return definition; }
        @Override public MaterialPart part() { return part; }
        @Override public BlockBehaviour.Properties properties() { return RuntimeProperties.block(definition, material.runtime(), false); }
        @Override public BlockSetType blockSetType() {
            var json = part.factoryData().json();
            return json.isJsonObject() && json.getAsJsonObject().has("block_set_type")
                    ? setType(ResourceLocation.parse(json.getAsJsonObject().get("block_set_type").getAsString())) : materialBlockSetType();
        }
        @Override public BlockSetType materialBlockSetType() { return setType(material.runtime().blockSetType()); }
        @Override public Supplier<BlockState> baseBlock() {
            return () -> RuntimeProperties.required(BuiltInRegistries.BLOCK, material.runtime().baseBlock()).defaultBlockState();
        }
        private static BlockSetType setType(ResourceLocation id) {
            return BlockSetType.values().filter(value -> ResourceLocation.parse(value.name()).equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown block set type: " + id));
        }
    }
    private static <T> void put(Map<ResourceLocation, T> map, ResourceLocation id, T value) {
        if (map.putIfAbsent(id, value) != null) throw new IllegalArgumentException("Duplicate runtime entry: " + id);
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
