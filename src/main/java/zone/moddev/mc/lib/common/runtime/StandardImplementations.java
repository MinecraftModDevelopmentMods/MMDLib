package zone.moddev.mc.lib.common.runtime;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.WoodType;

import zone.moddev.mc.lib.api.BlockContext;
import zone.moddev.mc.lib.api.BlockImplementation;
import zone.moddev.mc.lib.api.ItemContext;
import zone.moddev.mc.lib.api.ItemImplementation;
import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.common.types.MMDBlock;
import zone.moddev.mc.lib.common.types.MMDItem;
import zone.moddev.mc.lib.common.types.MMDMaterial;
import zone.moddev.mc.lib.common.types.MaterialPart;

/**
 * MMDLib's own item and block factories, registered through the same public {@link
 * MaterialExtensions#registerItemImplementation} / {@link
 * MaterialExtensions#registerBlockImplementation} calls an addon uses. Nothing in the runtime
 * registrar knows these IDs.
 */
public final class StandardImplementations {
  private StandardImplementations() {}

  /** Registers every built-in item and block implementation in {@code api}. */
  public static void install(MaterialExtensions api) {
    item(
        api,
        "pickaxe",
        StandardItems.Pickaxe.class,
        c -> new StandardItems.Pickaxe(c.tier(), c.properties()));
    item(api, "axe", StandardItems.Axe.class, c -> new StandardItems.Axe(c.tier(), c.properties()));
    item(
        api,
        "shovel",
        StandardItems.Shovel.class,
        c -> new StandardItems.Shovel(c.tier(), c.properties()));
    item(api, "hoe", StandardItems.Hoe.class, c -> new StandardItems.Hoe(c.tier(), c.properties()));
    item(
        api,
        "sword",
        StandardItems.Sword.class,
        c -> new StandardItems.Sword(c.tier(), c.properties()));
    item(
        api,
        "scythe",
        ScytheItem.class,
        c -> new ScytheItem(c.material(), c.tier(), c.properties()));
    api.registerItemImplementation(
        id("tool"),
        new ItemFactory<>(
            StandardItems.Tool.class,
            c ->
                new StandardItems.Tool(
                    c.tier(), StandardItems.ToolSettings.of(c.part()), c.properties()),
            false,
            StandardItems.ToolSettings::of));
    api.registerItemImplementation(
        id("armor"),
        new ItemFactory<>(
            StandardItems.Armor.class,
            c ->
                new StandardItems.Armor(
                    c.material(),
                    c.armor(),
                    c.factoryData()
                        .decode(ArmorItem.Type.CODEC.fieldOf("slot").codec())
                        .getOrThrow(),
                    tinted(c)),
            true,
            part ->
                part.factoryData()
                    .decode(ArmorItem.Type.CODEC.fieldOf("slot").codec())
                    .getOrThrow()));
    api.registerItemImplementation(
        id("horse_armor"),
        new ItemFactory<>(
            StandardItems.HorseArmor.class,
            c -> new StandardItems.HorseArmor(c.material(), c.armor(), tinted(c)),
            true,
            part -> {}));
    item(
        api,
        "bow",
        StandardItems.Bow.class,
        c ->
            new StandardItems.Bow(
                c.material(),
                c.tier()::getRepairIngredient,
                c.properties().durability(c.tier().getUses())));
    item(
        api,
        "crossbow",
        StandardItems.Crossbow.class,
        c ->
            new StandardItems.Crossbow(
                c.material(),
                c.tier()::getRepairIngredient,
                c.properties().durability(c.tier().getUses())));
    item(
        api,
        "shield",
        StandardItems.Shield.class,
        c ->
            new StandardItems.Shield(
                c.material(),
                c.tier()::getRepairIngredient,
                c.properties()
                    .durability(
                        new MaterialStats(c.material())
                            .integer(
                                "shield_durability",
                                Math.max(1, (int) (168 * c.material().properties().strength())),
                                1))));
    item(
        api,
        "shears",
        StandardItems.Shears.class,
        c ->
            new StandardItems.Shears(
                c.material(),
                c.tier()::getRepairIngredient,
                c.properties().durability(c.tier().getUses())));
    item(
        api,
        "fishing_rod",
        StandardItems.FishingRod.class,
        c ->
            new StandardItems.FishingRod(
                c.material(),
                c.tier()::getRepairIngredient,
                c.properties().durability(c.tier().getUses())));
    // Ingots, dusts, nuggets, ...: burn for fuel_<module> when set, else item_burn_time.
    item(
        api,
        "basic_item",
        StandardItems.Fuel.class,
        c -> {
          var stats = new MaterialStats(c.material());
          return new StandardItems.Fuel(
              c.material(),
              stats.integer(
                  "fuel_" + c.part().templateId().getPath(),
                  stats.integer("item_burn_time", 0, 0),
                  0),
              c.properties());
        });
    item(
        api,
        "arrow",
        MaterialAmmoItem.class,
        c -> new MaterialAmmoItem(c.material(), c.projectile(), c.properties()));
    item(
        api,
        "bolt",
        MaterialAmmoItem.class,
        c -> new MaterialAmmoItem(c.material(), c.projectile(), c.properties()));

    // A plain block, or a redstone block when factory_data.redstone is set.
    api.registerBlockImplementation(
        id("block"),
        new BlockFactory<>(
            Block.class,
            c ->
                RedstoneBlock.Settings.of(c.part())
                    .<Block>map(
                        settings ->
                            new RedstoneBlock(
                                c.properties(), settings, c.events(), c.definition(), c.material()))
                    .orElseGet(() -> new Block(c.properties())),
            RedstoneBlock.Settings::of,
            null));
    block(
        api,
        "ore",
        DropExperienceBlock.class,
        c -> new DropExperienceBlock(ConstantInt.of(0), c.properties()));
    block(api, "plate", PlateBlock.class, c -> new PlateBlock(c.properties().noOcclusion()));
    block(
        api,
        "bars",
        IronBarsBlock.class,
        c -> new IronBarsBlock(c.properties().noOcclusion().dynamicShape()));
    block(
        api,
        "bookshelf",
        StandardBlocks.Bookshelf.class,
        c -> new StandardBlocks.Bookshelf(c.properties()));
    block(
        api,
        "button",
        ButtonBlock.class,
        c -> new ButtonBlock(c.blockSetType(), 20, c.properties().noCollission()));
    block(api, "slab", SlabBlock.class, c -> new SlabBlock(c.properties()));
    block(
        api,
        "double_slab",
        CompatibilityDoubleSlabBlock.class,
        c -> new CompatibilityDoubleSlabBlock(c.properties()));
    block(
        api,
        "flower_pot",
        FlowerPotBlock.class,
        c -> new FlowerPotBlock(Blocks.AIR, c.properties().noOcclusion()));
    block(api, "ladder", LadderBlock.class, c -> new LadderBlock(c.properties().noOcclusion()));
    block(api, "lever", LeverBlock.class, c -> new LeverBlock(c.properties().noCollission()));
    api.registerBlockImplementation(
        id("pressure_plate"),
        new BlockFactory<>(
            PressurePlateBlock.class,
            c ->
                StandardBlocks.Detects.of(c.part())
                    .<PressurePlateBlock>map(
                        detects ->
                            new StandardBlocks.DetectorPressurePlate(
                                c.blockSetType(), detects, c.properties()))
                    .orElseGet(() -> new PressurePlateBlock(c.blockSetType(), c.properties())),
            StandardBlocks.Detects::of,
            null));
    // Vanilla weights: 15 for gold's light plate, 150 for iron's heavy plate.
    api.registerBlockImplementation(
        id("weighted_pressure_plate"),
        new BlockFactory<>(
            WeightedPressurePlateBlock.class,
            c ->
                new WeightedPressurePlateBlock(
                    maxWeight(c.part()), c.blockSetType(), c.properties()),
            StandardImplementations::maxWeight,
            null));
    block(
        api, "stairs", StairBlock.class, c -> new StairBlock(c.baseBlock().get(), c.properties()));
    // Doors and trapdoors always open like the material (e.g. iron needs redstone), whatever
    // factory_data says.
    block(
        api,
        "trapdoor",
        TrapDoorBlock.class,
        c ->
            new TrapDoorBlock(
                c.materialBlockSetType(), c.properties().noOcclusion().dynamicShape()));
    block(
        api,
        "tripwire_hook",
        TripWireHookBlock.class,
        c -> new TripWireHookBlock(c.properties().noCollission()));
    block(api, "wall", WallBlock.class, c -> new WallBlock(c.properties()));
    block(api, "fence", FenceBlock.class, c -> new FenceBlock(c.properties()));
    block(
        api,
        "fence_gate",
        FenceGateBlock.class,
        c -> new FenceGateBlock(WoodType.OAK, c.properties()));
    block(api, "anvil", MaterialAnvilBlock.class, c -> new MaterialAnvilBlock(c.properties()));
    api.registerBlockImplementation(
        id("door"),
        new BlockFactory<>(
            DoorBlock.class,
            c ->
                new DoorBlock(
                    c.materialBlockSetType(), c.properties().noOcclusion().dynamicShape()),
            part -> {},
            (block, c) -> new DoubleHighBlockItem(block, c.properties())));
  }

  private static int maxWeight(MaterialPart part) {
    var json = part.factoryData().json();
    if (!json.isJsonObject() || !json.getAsJsonObject().has("max_weight"))
      throw new IllegalArgumentException(
          "Weighted pressure plate needs factory_data.max_weight: " + part);
    int weight = json.getAsJsonObject().get("max_weight").getAsInt();
    if (weight < 1 || weight > 1024)
      throw new IllegalArgumentException("max_weight must be 1-1024: " + part);
    return weight;
  }

  private static Item.Properties tinted(ItemContext context) {
    var properties = context.properties();
    if (context.material().runtime().tintedArmor())
      properties.component(
          DataComponents.DYED_COLOR,
          new DyedItemColor(context.material().properties().color() & 0xFFFFFF, false));
    return properties;
  }

  private static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath("mmdlib", path);
  }

  private static <I extends Item> void item(
      MaterialExtensions api, String name, Class<I> type, Function<ItemContext, I> factory) {
    api.registerItemImplementation(id(name), new ItemFactory<>(type, factory, false, part -> {}));
  }

  private static <B extends Block> void block(
      MaterialExtensions api, String name, Class<B> type, Function<BlockContext, B> factory) {
    api.registerBlockImplementation(id(name), new BlockFactory<>(type, factory, part -> {}, null));
  }

  private record ItemFactory<I extends Item>(
      Class<I> itemClass,
      Function<ItemContext, I> factory,
      boolean usesArmorMaterial,
      Consumer<MaterialPart> validator)
      implements ItemImplementation<I> {
    @Override
    public I create(MMDMaterial material, MMDItem definition) {
      throw new IllegalStateException(
          "Created by the runtime registrar with an ItemContext: " + definition.name());
    }

    @Override
    public I create(ItemContext context) {
      return factory.apply(context);
    }

    @Override
    public void validate(MaterialPart part) {
      validator.accept(part);
    }
  }

  private record BlockFactory<B extends Block>(
      Class<B> blockClass,
      Function<BlockContext, B> factory,
      Consumer<MaterialPart> validator,
      BiFunction<Block, ItemContext, Item> item)
      implements BlockImplementation<B> {
    @Override
    public B create(MMDMaterial material, MMDBlock definition) {
      throw new IllegalStateException(
          "Created by the runtime registrar with a BlockContext: " + definition.name());
    }

    @Override
    public B create(BlockContext context) {
      return factory.apply(context);
    }

    @Override
    public void validate(MaterialPart part) {
      validator.accept(part);
    }

    @Override
    public Item createItem(Block block, ItemContext context) {
      return item == null
          ? BlockImplementation.super.createItem(block, context)
          : item.apply(block, context);
    }
  }
}
