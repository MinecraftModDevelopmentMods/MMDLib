package zone.moddev.mc.companion;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
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
import zone.moddev.mc.lib.common.runtime.DropsContents;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.machine.feature.FeatureHost;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;
import zone.moddev.mc.lib.machine.neoforge.FeatureSync;
import zone.moddev.mc.lib.machine.neoforge.StorageGroups;

/** The dual crusher deliberately runs two lanes over the same input and output groups. */
public final class ProcessorEntity extends DemoEntity
    implements ScreenHost, DropsContents, Clearable {
  public enum Kind {
    CRUSHER,
    TRANSMUTER,
    DUAL,
    LOGISTICS
  }

  public final ItemStorage items;
  public final AutomationInventory automation;
  public final FluidStorage fluids = new FluidStorage().onChange(this::setChanged);
  public final List<RecipeProcessor> lanes;
  public final FeatureHost features =
      new FeatureHost(this::setChanged, () -> FeatureSync.send(this));
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
    var inventory = MachineInventory.builder().group("energy", StorageGroups.energy(energy));
    if (kind == Kind.TRANSMUTER) {
      // One key, and one item: the recipe consumes the whole slot before inserting gold.
      items = new ItemStorage(1).limit(1, 0).onChange(this::setChanged);
      inventory.group("slot", StorageGroups.items(items, 0));
    } else {
      items =
          new ItemStorage(1 + outputSlots.length)
              .role(Role.INPUT, 0)
              .role(kind == Kind.CRUSHER ? Role.STORAGE : Role.OUTPUT, outputSlots)
              .onChange(this::setChanged);
      inventory
          .group("input", StorageGroups.items(items, 0))
          .group("output", StorageGroups.items(items, outputSlots));
    }
    var shared = inventory.build();
    var recipeType = kind == Kind.TRANSMUTER ? CompanionMod.TRANSMUTING : CompanionMod.CRUSHING;
    var processors = new ArrayList<RecipeProcessor>();
    for (int i = 0; i < (kind == Kind.DUAL ? 2 : 1); i++) {
      var processor =
          new RecipeProcessor(
              "lane_" + i,
              recipeType,
              shared,
              Component.translatable("screen.mmdlib_companion.processor", i + 1));
      processors.add(processor);
      features.add(processor);
    }
    lanes = List.copyOf(processors);
    var groups = new ArrayList<ResourceGroup<?>>();
    groups.add(
        itemGroup("input", 0x568bd8, kind == Kind.TRANSMUTER ? IoMode.BOTH : IoMode.INPUT, 0));
    if (kind != Kind.TRANSMUTER)
      groups.add(
          itemGroup(
              "output", 0xe48f45, kind == Kind.CRUSHER ? IoMode.BOTH : IoMode.OUTPUT, outputSlots));
    int[] colours = {0x64bfc1, 0xa678cb, 0xe0c55d, 0xce668e};
    for (int i = 0; i < fluids.getTanks(); i++) {
      final int tank = i;
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
      group.bind(
          this::facing,
          side -> fluids.view(index -> index == tank ? group.mode(side) : IoMode.DISABLED));
      groups.add(group);
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
    energyGroup.bind(this::facing, side -> energy.view(() -> energyGroup.mode(side)));
    groups.add(energyGroup);
    automation = new AutomationInventory(this, groups);
    var outputParts = new ArrayList<Object>();
    for (var group : groups)
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
    return group.bind(
        this::facing,
        side -> items.view(index -> group.contains(index) ? group.mode(side) : IoMode.DISABLED));
  }

  @Override
  public RedstoneControl screenRedstone() {
    return redstone;
  }

  public AutomationInventory screenAutomation() {
    return automation;
  }

  @Override
  public void serverTick() {
    redstone.readInputs();
    features.tick(
        level,
        kind != Kind.LOGISTICS
            && redstoneAllowsRun()
            && energy.stored() >= energy.capacity() * reservePercent / 100);
    AutomationTransfers.tick(automation, energy);
    redstone.updateOutputs();
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

  private boolean redstoneAllowsRun() {
    return !redstone.paused();
  }

  public void toggle(int index) {
    if (index < 0 || index >= lanes.size()) return;
    var processor = lanes.get(index);
    if (processor.paused()) processor.resume();
    else processor.interrupt();
    // Redstone can hold the feature tick, so persist and sync a manual toggle immediately.
    setChanged();
    FeatureSync.send(this);
  }

  public int getProgress() {
    return lanes.getFirst().progress();
  }

  @Override
  public List<zone.moddev.mc.lib.api.stats.StatsProvider> screenStats() {
    var parts = new ArrayList<zone.moddev.mc.lib.api.stats.StatsProvider>();
    parts.add(energy);
    parts.add(items);
    parts.add(fluids);
    parts.addAll(lanes);
    return List.copyOf(parts);
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

  public int getTime() {
    return lanes.getFirst().time();
  }

  public int getSecondProgress() {
    return lanes.size() == 2 ? lanes.get(1).progress() : 0;
  }

  public int getSecondTime() {
    return lanes.size() == 2 ? lanes.get(1).time() : 0;
  }

  public boolean getPaused() {
    return lanes.getFirst().paused();
  }

  public boolean getSecondPaused() {
    return lanes.size() == 2 && lanes.get(1).paused();
  }

  public List<Component> getWarnings() {
    var warnings = new ArrayList<Component>();
    if (energy.stored() < energy.capacity() * reservePercent / 100)
      warnings.add(
          Component.translatable("screen.mmdlib_companion.reserve.waiting", reservePercent));
    if (!redstoneAllowsRun())
      warnings.add(Component.translatable("screen.mmdlib_companion.redstone.waiting"));
    for (int i = 0; i < lanes.size(); i++) {
      var status = lanes.get(i).status();
      if (status == RecipeProcessor.Status.STALLED || status == RecipeProcessor.Status.BLOCKED)
        warnings.add(
            Component.translatable("screen.mmdlib_companion.lane_" + (i == 0 ? "a" : "b"))
                .append(": ")
                .append(
                    Component.translatable(
                        "status.mmdlib_companion."
                            + status.name().toLowerCase(java.util.Locale.ROOT))));
    }
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
  public void dropContents(Level level, BlockPos pos) {
    items.dropAll(level, pos);
    automation.dropAll(
        payload ->
            net.minecraft.world.Containers.dropItemStack(
                level,
                pos.getX() + .5,
                pos.getY() + .5,
                pos.getZ() + .5,
                RecoveryCapsuleItem.create(payload)));
  }

  @Override
  public void clearContent() {
    automation.clear();
    lanes.forEach(RecipeProcessor::reset);
    for (int slot = 0; slot < items.getSlots(); slot++) {
      items.setPlan(slot, SlotPlan.NONE);
      items.setStackInSlot(slot, ItemStack.EMPTY);
    }
    energy.setStored(0);
    for (int i = 0; i < fluids.getTanks(); i++) fluids.tank(i).setFluid(FluidStack.EMPTY);
    setChanged();
    FeatureSync.send(this);
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.put("items", items.serializeNBT(registries));
    tag.put("automation", automation.save(registries));
    tag.put("redstone_control", redstone.save(registries));
    tag.putString("machine_name", machineName);
    tag.putInt("reserve_percent", reservePercent);
    if (hasTank()) tag.put("fluids", fluids.serializeNBT(registries));
    tag.put("features", features.save(registries.createSerializationContext(NbtOps.INSTANCE)));
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    if (FeatureSync.read(features, tag, registries)) return;
    super.loadAdditional(tag, registries);
    int savedMode = tag.getInt("redstone_mode");
    int redstoneMode = savedMode >= 0 && savedMode <= 2 ? savedMode : 0;
    if (tag.contains("redstone_control"))
      redstone.load(tag.getCompound("redstone_control"), registries);
    else redstone.migrate(redstoneMode);
    String savedName = tag.getString("machine_name");
    machineName =
        !savedName.isBlank()
                && savedName.length() <= 32
                && savedName.codePoints().noneMatch(Character::isISOControl)
            ? savedName.strip()
            : "Crusher";
    reservePercent = Math.clamp(tag.getInt("reserve_percent"), 0, 100);
    items.deserializeNBT(registries, tag.getCompound("items"));
    automation.load(tag.getCompound("automation"), registries);
    if (hasTank() && tag.contains("fluids"))
      fluids.deserializeNBT(registries, tag.getCompound("fluids"));
    features.load(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("features"));
  }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    return FeatureSync.updateTag(features, registries);
  }

  @Override
  public Packet<ClientGamePacketListener> getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }
}
