package zone.moddev.mc.companion;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import zone.moddev.mc.lib.api.MaterialEventTypes;
import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.api.capabilities.MachineCapabilities;
import zone.moddev.mc.lib.gui.runtime.ScreenClick;
import zone.moddev.mc.lib.machine.neoforge.MachineRecipeTypes;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/** A separately packaged consumer of MMDLib's processor and screen APIs. */
@Mod(CompanionMod.ID)
public final class CompanionMod {
  public static final String ID = "mmdlib_companion";
  private static final DeferredRegister<Block> BLOCKS =
      DeferredRegister.create(Registries.BLOCK, ID);
  private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ID);
  private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
      DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
  private static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
      DeferredRegister.create(Registries.RECIPE_TYPE, ID);
  private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
      DeferredRegister.create(Registries.RECIPE_SERIALIZER, ID);
  private static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);

  public static final MachineRecipeType CRUSHING =
      MachineRecipeTypes.register(RECIPE_TYPES, SERIALIZERS, "crushing");
  public static final MachineRecipeType TRANSMUTING =
      MachineRecipeTypes.register(RECIPE_TYPES, SERIALIZERS, "transmuting");
  public static final Machine CRUSHER = new Machine("crusher", ProcessorEntity.Kind.CRUSHER);
  public static final Machine TRANSMUTER =
      new Machine("transmuter", ProcessorEntity.Kind.TRANSMUTER);
  public static final Machine DUAL_CRUSHER = new Machine("dual_crusher", ProcessorEntity.Kind.DUAL);
  public static final Machine CREATIVE_POWER = new Machine("creative_power", null);
  public static final Machine LOGISTICS = new Machine("logistics", ProcessorEntity.Kind.LOGISTICS);
  public static final List<Machine> MACHINES =
      List.of(CRUSHER, TRANSMUTER, DUAL_CRUSHER, CREATIVE_POWER, LOGISTICS);

  static {
    TABS.register(
        "machines",
        () ->
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.mmdlib_companion"))
                .icon(() -> new ItemStack(CRUSHER.block.get()))
                .displayItems(
                    (parameters, output) -> {
                      MACHINES.forEach(machine -> output.accept(machine.block.get()));
                      CompanionAddons.items().forEach(item -> output.accept(item.get()));
                    })
                .build());
  }

  public CompanionMod(IEventBus bus) {
    CompanionWidgets.register();
    CompanionAddons.register(bus);
    BLOCKS.register(bus);
    ITEMS.register(bus);
    ENTITIES.register(bus);
    RECIPE_TYPES.register(bus);
    SERIALIZERS.register(bus);
    TABS.register(bus);
    bus.addListener(CompanionMod::capabilities);
    MaterialExtensions.GLOBAL
        .events()
        .registerCallback(
            id("tank_container"),
            MaterialEventTypes.GUI_CLICK,
            CompanionMod::transferTankContainer);
    for (int lane = 0; lane < 2; lane++) {
      int index = lane;
      MaterialExtensions.GLOBAL
          .events()
          .registerCallback(
              id("toggle_" + lane),
              MaterialEventTypes.GUI_CLICK,
              click -> {
                if (click.menu().blockEntity instanceof ProcessorEntity machine)
                  machine.toggle(index);
              });
    }
  }

  private static void transferTankContainer(ScreenClick click) {
    if (!(click.menu().blockEntity instanceof ProcessorEntity machine) || !machine.hasTank())
      return;
    var carried = click.menu().getCarried();
    if (carried.isEmpty() || FluidUtil.getFluidHandler(carried).isEmpty()) return;
    var inventory = click.player().getCapability(Capabilities.ItemHandler.ENTITY);
    if (inventory == null) return;
    // Work on the server's cursor stack. NeoForge handles partial containers and stacked results.
    var result =
        FluidUtil.tryEmptyContainerAndStow(
            carried, machine.fluids, inventory, Integer.MAX_VALUE, click.player(), true);
    if (!result.isSuccess())
      result =
          FluidUtil.tryFillContainerAndStow(
              carried, machine.fluids, inventory, Integer.MAX_VALUE, click.player(), true);
    if (result.isSuccess()) {
      click.menu().setCarried(result.getResult());
      click.menu().broadcastChanges();
    } else {
      click
          .player()
          .displayClientMessage(
              Component.translatable("screen.mmdlib_companion.tank.refused"), true);
    }
  }

  private static void capabilities(RegisterCapabilitiesEvent event) {
    CompanionAddons.capabilities(event);
    for (var machine : MACHINES) {
      if (machine == CREATIVE_POWER) {
        MachineCapabilities.energy(
            event,
            machine.type.get(),
            entity -> entity.energy,
            (entity, side) ->
                ((CreativePowerEntity) entity)
                    .automation.mode(
                        zone.moddev.mc.lib.api.automation.ResourceType.ENERGY, 0, side));
        continue;
      }
      event.registerBlockEntity(
          Capabilities.ItemHandler.BLOCK,
          machine.type.get(),
          (entity, side) -> {
            var p = (ProcessorEntity) entity;
            return p.items.view(
                slot ->
                    p.automation.mode(
                        zone.moddev.mc.lib.api.automation.ResourceType.ITEMS, slot, side));
          });
      event.registerBlockEntity(
          Capabilities.FluidHandler.BLOCK,
          machine.type.get(),
          (entity, side) -> {
            var p = (ProcessorEntity) entity;
            return p.hasTank()
                ? p.fluids.view(
                    tank ->
                        p.automation.mode(
                            zone.moddev.mc.lib.api.automation.ResourceType.FLUIDS, tank, side))
                : null;
          });
      event.registerBlockEntity(
          Capabilities.EnergyStorage.BLOCK,
          machine.type.get(),
          (entity, side) -> {
            var p = (ProcessorEntity) entity;
            return side == null
                ? p.energy.view(zone.moddev.mc.lib.api.capabilities.IoMode.BOTH)
                : p.energy.view(
                    () ->
                        p.automation.mode(
                            zone.moddev.mc.lib.api.automation.ResourceType.ENERGY, 0, side));
          });
    }
  }

  public static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(ID, path);
  }

  /** Each block has its own type and XML screen, while processors share one implementation. */
  public static final class Machine {
    public final DeferredHolder<Block, DemoBlock> block;
    public final DeferredHolder<BlockEntityType<?>, BlockEntityType<DemoEntity>> type;
    private final ProcessorEntity.Kind kind;

    private Machine(String name, ProcessorEntity.Kind kind) {
      this.kind = kind;
      block =
          BLOCKS.register(
              name,
              () ->
                  new DemoBlock(
                      BlockBehaviour.Properties.of().strength(3.5F).requiresCorrectToolForDrops(),
                      this::entityType));
      type =
          ENTITIES.register(
              name, () -> BlockEntityType.Builder.of(this::create, block.get()).build(null));
      ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private BlockEntityType<DemoEntity> entityType() {
      return type.get();
    }

    private DemoEntity create(BlockPos pos, BlockState state) {
      return kind == null
          ? new CreativePowerEntity(type.get(), pos, state)
          : new ProcessorEntity(type.get(), pos, state, kind);
    }
  }
}
