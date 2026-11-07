package zone.moddev.mc.companion;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import zone.moddev.mc.lib.api.automation.*;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.api.redstone.RedstoneOutput;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor;
import zone.moddev.mc.lib.machine.neoforge.BasePoweredMachine;
import zone.moddev.mc.lib.machine.neoforge.MachineInventoryLifecycle;
import zone.moddev.mc.lib.machine.neoforge.MachineResources;

/** The dual crusher deliberately runs two lanes over the same input and output groups. */
public final class ProcessorEntity extends BasePoweredMachine {
  public enum Kind {
    CRUSHER,
    TRANSMUTER,
    DUAL,
    LOGISTICS
  }

  public final ItemStorage items;
  public final AutomationInventory automation;
  public final MachineResources resources;
  public final MachineInventoryLifecycle inventoryLifecycle;
  public final FluidStorage fluids = new FluidStorage().onChange(this::setChanged);
  public final List<RecipeProcessor> lanes;
  private final Kind kind;
  public final RedstoneControl redstone;
  private String machineName = "Crusher";
  private int reservePercent;

  public Component getMachineName() {
    return Component.literal(machineName);
  }

  public int getReservePercent() {
    return reservePercent;
  }

  public boolean rename(String name) {
    if (name.isBlank() || name.length() > 32 || name.codePoints().anyMatch(Character::isISOControl))
      return false;
    machineName = name.strip();
    setChanged();
    return true;
  }

  public boolean setReservePercent(int value) {
    if (value < 0 || value > 100) return false;
    reservePercent = value;
    setChanged();
    return true;
  }

  public ProcessorEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Kind kind) {
    super(type, pos, state);
    this.kind = kind;
    if (kind == Kind.CRUSHER)
      fluids.addTank(
          4_000,
          Role.STORAGE,
          fluid -> fluid.getFluid() == Fluids.WATER || fluid.getFluid() == Fluids.LAVA);
    if (kind == Kind.LOGISTICS)
      for (int i = 0; i < 4; i++) fluids.addTank(4000, i < 2 ? Role.INPUT : Role.OUTPUT);
    int[] outputSlots = kind == Kind.CRUSHER ? new int[] {1, 2} : new int[] {1};
    resources = new MachineResources(this, this::facing);
    if (kind == Kind.TRANSMUTER) {
      // One key, and one item: the recipe consumes the whole slot before inserting gold.
      items = new ItemStorage(1).limit(1, 0).onChange(this::setChanged);
    } else {
      items =
          new ItemStorage(1 + outputSlots.length)
              .role(Role.INPUT, 0)
              .role(kind == Kind.CRUSHER ? Role.STORAGE : Role.OUTPUT, outputSlots)
              .onChange(this::setChanged);
    }
    var recipeType = kind == Kind.TRANSMUTER ? CompanionMod.TRANSMUTING : CompanionMod.CRUSHING;
    resources.items(
        itemGroup("input", 0x568bd8, kind == Kind.TRANSMUTER ? IoMode.BOTH : IoMode.INPUT, 0),
        items);
    if (kind != Kind.TRANSMUTER)
      resources.items(
          itemGroup(
              "output", 0xe48f45, kind == Kind.CRUSHER ? IoMode.BOTH : IoMode.OUTPUT, outputSlots),
          items);
    items.slotId(0, "input");
    for (int i = 1; i < items.getSlots(); i++) items.slotId(i, "output_" + i);
    int[] colours = {0x64bfc1, 0xa678cb, 0xe0c55d, 0xce668e};
    for (int i = 0; i < fluids.getTanks(); i++) {
      var group =
          new ResourceGroup<>(
              "tank_" + i,
              Component.translatable("screen.mmdlib_companion.group.tank", i + 1),
              colours[i],
              ResourceType.FLUIDS,
              kind == Kind.CRUSHER ? IoMode.BOTH : (i < 2 ? IoMode.INPUT : IoMode.OUTPUT),
              List.of(i),
              List.of(i == 0 ? "tank" : "tank_" + i),
              new SideConfig(
                  kind == Kind.CRUSHER ? IoMode.BOTH : (i < 2 ? IoMode.INPUT : IoMode.OUTPUT)));
      resources.fluids(group, fluids);
      fluids.tankId(i, group.id());
    }
    var energyGroup =
        new ResourceGroup<>(
            "energy",
            Component.translatable("screen.mmdlib_companion.group.energy"),
            0xd85b5b,
            ResourceType.ENERGY,
            kind == Kind.LOGISTICS ? IoMode.BOTH : IoMode.INPUT,
            List.of(0),
            List.of("energy"),
            new SideConfig(kind == Kind.LOGISTICS ? IoMode.BOTH : IoMode.INPUT));
    if (kind == Kind.LOGISTICS) energy.setRates(1000, 1000);
    resources.energy(energyGroup, energy);
    if (kind == Kind.TRANSMUTER) resources.recipeGroup("slot", "input");
    if (kind != Kind.LOGISTICS)
      resources.recipeInputs(recipeType, kind == Kind.TRANSMUTER ? "slot" : "input");
    resources.build();
    automation = resources.automation();
    var processors = new ArrayList<RecipeProcessor>();
    for (int i = 0; i < (kind == Kind.DUAL ? 2 : 1); i++) {
      var processor =
          new RecipeProcessor(
              "lane_" + i,
              recipeType,
              resources.recipes(),
              Component.translatable("screen.mmdlib_companion.processor", i + 1));
      processors.add(processor);
      features.add(processor);
    }
    lanes = List.copyOf(processors);
    inventoryLifecycle =
        poweredInventory(automation).items("items", items).fluids("fluids", fluids);
    var outputParts = new ArrayList<Object>();
    for (var group : automation.groups())
      outputParts.add(
          RedstoneOutput.resource(
              group,
              () -> {
                if (group.type() == ResourceType.ENERGY)
                  return energy.capacity() == 0 ? 0 : (double) energy.stored() / energy.capacity();
                double amount = 0, capacity = 0;
                for (int index : group.members()) {
                  if (group.type() == ResourceType.ITEMS && index < items.getSlots()) {
                    var stack = items.getStackInSlot(index);
                    int limit =
                        Math.min(
                            items.getSlotLimit(index),
                            stack.isEmpty() ? 64 : stack.getMaxStackSize());
                    if (limit > 0) {
                      amount += (double) stack.getCount() / limit;
                      capacity++;
                    }
                  } else if (group.type() == ResourceType.FLUIDS && index < fluids.getTanks()) {
                    amount += fluids.getFluidInTank(index).getAmount();
                    capacity += fluids.getTankCapacity(index);
                  }
                }
                return capacity == 0 ? 0 : amount / capacity;
              }));
    if (kind != Kind.LOGISTICS) outputParts.add(features);
    redstone = new RedstoneControl(this, outputParts);
    zone.moddev.mc.lib.gui.runtime.ScreenInventoryValidation.validate(this);
  }

  private ResourceGroup<net.neoforged.neoforge.items.IItemHandler> itemGroup(
      String id, int colour, IoMode mode, int... slots) {
    var group =
        new ResourceGroup<>(
            id,
            Component.translatable("screen.mmdlib_companion.group." + id),
            colour,
            ResourceType.ITEMS,
            mode,
            java.util.Arrays.stream(slots).boxed().toList(),
            java.util.stream.IntStream.range(0, slots.length)
                .mapToObj(i -> i == 0 ? id : id + "_" + (i + 1))
                .toList(),
            new SideConfig(mode));
    return group;
  }

  @Override
  public RedstoneControl screenRedstone() {
    return redstone;
  }

  public FluidStack getTank1() {
    return fluids.getFluidInTank(1);
  }

  public FluidStack getTank2() {
    return fluids.getFluidInTank(2);
  }

  public FluidStack getTank3() {
    return fluids.getFluidInTank(3);
  }

  public void toggle(int index) {
    if (index < 0 || index >= lanes.size()) return;
    var processor = lanes.get(index);
    features.setProcessorPaused(processor.key(), !processor.paused());
  }

  public boolean hasTank() {
    return fluids.getTanks() > 0;
  }

  public FluidStack getTankFluid() {
    return fluids.getFluidInTank(0);
  }

  public int getTankCapacity() {
    return fluids.getTankCapacity(0);
  }

  public int getSecondProgress() {
    return lanes.size() == 2 ? lanes.get(1).progress() : 0;
  }

  public int getSecondTime() {
    return lanes.size() == 2 ? lanes.get(1).time() : 0;
  }

  @Override
  public List<Component> getWarnings() {
    var warnings = new ArrayList<Component>(super.getWarnings());
    if (energy.stored() < energy.capacity() * reservePercent / 100)
      warnings.add(
          Component.translatable("screen.mmdlib_companion.reserve.waiting", reservePercent));
    return warnings;
  }

  @Override
  public ItemStorage screenItems() {
    return items;
  }

  @Override
  public int[] screenRecipeSlots(ResourceLocation category) {
    var recipeType = kind == Kind.TRANSMUTER ? CompanionMod.TRANSMUTING : CompanionMod.CRUSHING;
    return recipeType.id().equals(category) ? new int[] {0} : new int[0];
  }

  @Override
  protected void saveMachineData(CompoundTag tag, HolderLookup.Provider registries) {
    tag.putString("machine_name", machineName);
    tag.putInt("reserve_percent", reservePercent);
  }

  @Override
  protected void loadMachineData(CompoundTag tag, HolderLookup.Provider registries) {
    String savedName = tag.getString("machine_name");
    machineName =
        !savedName.isBlank()
                && savedName.length() <= 32
                && savedName.codePoints().noneMatch(Character::isISOControl)
            ? savedName.strip()
            : "Crusher";
    reservePercent = Math.clamp(tag.getInt("reserve_percent"), 0, 100);
  }

  @Override
  protected MachineInventoryLifecycle machineInventory() {
    return inventoryLifecycle;
  }

  @Override
  protected boolean processingAllowed() {
    return kind != Kind.LOGISTICS && energy.stored() >= energy.capacity() * reservePercent / 100;
  }

  @Override
  protected void recoverResources(Level level, BlockPos pos, CompoundTag payload) {
    net.minecraft.world.Containers.dropItemStack(
        level,
        pos.getX() + .5,
        pos.getY() + .5,
        pos.getZ() + .5,
        RecoveryCapsuleItem.create(payload));
  }
}
